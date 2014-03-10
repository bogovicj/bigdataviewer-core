package bdv.viewer.render;

import java.util.ArrayList;

import net.imglib2.realtransform.AffineTransform3D;
import bdv.img.cache.CacheHints;
import bdv.img.cache.VolatileGlobalCellCache.LoadingStrategy;
import bdv.util.MipmapTransforms;
import bdv.viewer.Source;

/**
 * The standard mipmap ordering strategy for local hdf5 data. Assumes that
 * mipmap indices in the source are ordered by decreasing resolution. Finds the
 * mipmap level that best matches the given screen scale for the given source.
 * Then, starting from this best level render all levels down to the lowest
 * resolution. For prefetching reverse that order (lowest resolution is
 * prefetched first).
 *
 * Additionally, when moving between time-points the following hack is used:
 * When scrolling through time, we often get frames for which no data was loaded
 * yet. To speed up rendering in these cases, use only two mipmap levels: the
 * optimal and the coarsest. By doing this, we require at most two passes over
 * the image at the expense of ignoring data present in intermediate mipmap
 * levels. The assumption is, that we will either be moving back and forth
 * between images that have all data present already or that we move to a new
 * image with no data present at all.
 *
 * @author Tobias Pietzsch <tobias.pietzsch@gmail.com>
 */
public class DefaultMipmapOrdering implements MipmapOrdering
{
	private final Source< ? > source;

	private final LoadingStrategy renderLoadingStrategy;

	private final LoadingStrategy prefetchLoadingStrategy;

	private final boolean renderEnqueuToFront;

	private final boolean prefetchEnqueuToFront;

	private final ArrayList< Level > precomputedLevels;

	/**
	 * @param source
	 * @param createHints
	 *            If true, {@link Level}s are created with {@link CacheHints}
	 *            computed as follows. {@link LoadingStrategy} and enqueue order
	 *            are set as specified in the remaining parameters. Priority is
	 *            set such that the coarsest mipmap level has highest priority.
	 *            If false, {@link Level}s are created with {@code null}
	 *            {@link CacheHints}.
	 * @param renderLoadingStrategy
	 * @param renderEnqueuToFront
	 * @param prefetchLoadingStrategy
	 * @param prefetchEnqueuToFront
	 */
	public DefaultMipmapOrdering( final Source< ? > source,
			final boolean createHints,
			final LoadingStrategy renderLoadingStrategy,
			final boolean renderEnqueuToFront,
			final LoadingStrategy prefetchLoadingStrategy,
			final boolean prefetchEnqueuToFront )
	{
		this.source = source;
		this.renderLoadingStrategy = renderLoadingStrategy;
		this.renderEnqueuToFront = renderEnqueuToFront;
		this.prefetchLoadingStrategy = prefetchLoadingStrategy;
		this.prefetchEnqueuToFront = prefetchEnqueuToFront;

		precomputedLevels = new ArrayList< Level >();
		final int numMipmapLevels = source.getNumMipmapLevels();
		final int maxLevel = numMipmapLevels - 1;
		for ( int level = 0; level < numMipmapLevels; ++level )
		{
			final int priority = maxLevel - level;
			final CacheHints renderCacheHints = createHints ? new CacheHints( renderLoadingStrategy, priority, renderEnqueuToFront ) : null;
			final CacheHints prefetchCacheHints = createHints ? new CacheHints( prefetchLoadingStrategy, priority, prefetchEnqueuToFront ) : null;
			precomputedLevels.add( new Level( level, 0, 0, renderCacheHints, prefetchCacheHints ) );
		}
	}

	public DefaultMipmapOrdering( final Source< ? > source )
	{
		this( source, false, null, false, null, false );
	}

	@Override
	public MipmapHints getMipmapHints( final AffineTransform3D screenTransform, final int timepoint, final int previousTimepoint )
	{
		final int bestLevel = MipmapTransforms.getBestMipMapLevel( screenTransform, source, timepoint );
		final int numMipmapLevels = source.getNumMipmapLevels();
		final int maxLevel = numMipmapLevels - 1;
		boolean renewHintsAfterPaintingOnce = false;
		final ArrayList< Level > levels = new ArrayList< Level >();
		if ( timepoint != previousTimepoint )
		{
			// When scrolling through time, we often get frames for which no
			// data was loaded yet. To speed up rendering in these cases, use
			// only two mipmap levels: the optimal and the coarsest. By doing
			// this, we require at most two passes over the image at the expense
			// of ignoring data present in intermediate mipmap levels. The
			// assumption is, that we will either be moving back and forth
			// between images that have all data present already or that we move
			// to a new image with no data present at all.
			levels.add( getLevel( bestLevel, 0, 1 ) );
			if ( maxLevel != bestLevel )
				levels.add( getLevel( maxLevel, 1, 0 ) );

			// slight abuse of newFrameRequest: we only want this two-pass
			// rendering to happen once then switch to normal multi-pass
			// rendering if we remain longer on this frame.
			renewHintsAfterPaintingOnce = true;
		}
		else
			for ( int i = bestLevel; i < numMipmapLevels; ++i )
				levels.add( getLevel( i, i, -i ) );
		return new MipmapHints( levels, renewHintsAfterPaintingOnce );
	}

	private Level getLevel( final int mipmapLevel, final int renderOrder, final int prefetchOrder )
	{
		final Level l = precomputedLevels.get( mipmapLevel );
		return new Level( mipmapLevel, renderOrder, prefetchOrder, l.getRenderCacheHints(), l.getPrefetchCacheHints() );
	}
}
