package bdv.img.catmaid;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;
import bdv.AbstractViewerImgLoader;
import bdv.img.cache.CacheHints;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.LoadingStrategy;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileImgCells;
import bdv.img.cache.VolatileImgCells.CellCache;

public class CatmaidImageLoader extends AbstractViewerImgLoader< ARGBType, VolatileARGBType >
{
	private final int numScales;

	private final double[][] mipmapResolutions;

	private final AffineTransform3D[] mipmapTransforms;

	private final long[][] imageDimensions;

	private final int[][] blockDimensions;

	private final VolatileGlobalCellCache< VolatileIntArray > cache;

	public CatmaidImageLoader(
			final long width,
			final long height,
			final long depth,
			final int numScales,
			final String urlFormat,
			final int tileWidth,
			final int tileHeight )
	{
		super( new ARGBType(), new VolatileARGBType() );
		this.numScales = numScales;

		mipmapResolutions = new double[ numScales ][];
		imageDimensions = new long[ numScales ][];
		blockDimensions = new int[ numScales ][];
		mipmapTransforms = new AffineTransform3D[ numScales ];
		for ( int l = 0; l < numScales; ++l )
		{
			mipmapResolutions[ l ] = new double[] { 1 << l, 1 << l, 1 };
			imageDimensions[ l ] = new long[] { width >> l, height >> l, depth };
			blockDimensions[ l ] = new int[] { tileWidth, tileHeight, 1 };

			final AffineTransform3D mipmapTransform = new AffineTransform3D();
			mipmapTransform.set( mipmapResolutions[ l ][ 0 ], 0, 0 );
			mipmapTransform.set( mipmapResolutions[ l ][ 1 ], 1, 1 );
			mipmapTransform.set( 0.5 * ( mipmapResolutions[ l ][ 0 ] - 1 ), 0, 3 );
			mipmapTransform.set( 0.5 * ( mipmapResolutions[ l ][ 0 ] - 1 ), 1, 3 );
			mipmapTransforms[ l ] = mipmapTransform;
		}

		cache = new VolatileGlobalCellCache< VolatileIntArray >(
				new CatmaidVolatileIntArrayLoader( urlFormat, tileWidth, tileHeight ), 1, 1, numScales, 10 );
	}

	final static public int getNumScales( long width, long height, final long tileWidth, final long tileHeight )
	{
		int i = 1;

		while ( ( width >>= 1 ) > tileWidth && ( height >>= 1 ) > tileHeight )
			++i;

		return i;
	}

	@Override
	public RandomAccessibleInterval< ARGBType > getImage( final ViewId view, final int level )
	{
		final CachedCellImg< ARGBType, VolatileIntArray >  img = prepareCachedImage( view, level, LoadingStrategy.BLOCKING );
		final ARGBType linkedType = new ARGBType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public RandomAccessibleInterval< VolatileARGBType > getVolatileImage( final ViewId view, final int level )
	{
		final CachedCellImg< VolatileARGBType, VolatileIntArray >  img = prepareCachedImage( view, level, LoadingStrategy.VOLATILE );
		final VolatileARGBType linkedType = new VolatileARGBType( img );
		img.setLinkedType( linkedType );
		return img;
	}

	@Override
	public double[][] getMipmapResolutions( final int setup )
	{
		return mipmapResolutions;
	}

	@Override
	public int numMipmapLevels( final int setup )
	{
		return numScales;
	}

	/**
	 * (Almost) create a {@link CachedCellImg} backed by the cache.
	 * The created image needs a {@link NativeImg#setLinkedType(net.imglib2.type.Type) linked type} before it can be used.
	 * The type should be either {@link ARGBType} and {@link VolatileARGBType}.
	 */
	protected < T extends NativeType< T > > CachedCellImg< T, VolatileIntArray > prepareCachedImage( final ViewId view, final int level, final LoadingStrategy loadingStrategy )
	{
		final long[] dimensions = imageDimensions[ level ];
		final int[] cellDimensions = blockDimensions[ level ];

		final int priority = numScales - 1 - level;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
		final CellCache< VolatileIntArray > c = cache.new VolatileCellCache( view.getTimePointId(), view.getViewSetupId(), level, cacheHints );
		final VolatileImgCells< VolatileIntArray > cells = new VolatileImgCells< VolatileIntArray >( c, 1, dimensions, cellDimensions );
		final CachedCellImg< T, VolatileIntArray > img = new CachedCellImg< T, VolatileIntArray >( cells );
		return img;
	}

	@Override
	public VolatileGlobalCellCache< VolatileIntArray > getCache()
	{
		return cache;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms( final int setup )
	{
		return mipmapTransforms;
	}
}
