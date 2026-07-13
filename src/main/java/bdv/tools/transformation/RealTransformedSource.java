/*
 * #%L
 * BigDataViewer core classes with minimal dependencies.
 * %%
 * Copyright (C) 2012 - 2026 BigDataViewer developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.tools.transformation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.render.DefaultMipmapOrdering;
import bdv.viewer.render.MipmapOrdering;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.interval.FacesSteps;
import net.imglib2.realtransform.interval.IntervalSamplingMethod;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

/**
 * A {@link Source} that wraps another {@link Source} and warps it with an
 * {@link InvertibleRealTransform} applied in <em>world</em> coordinates. This
 * class is intended for use with general, potentially non-affine
 * transformations. For affines, use {@link TransformedSource}.
 * <p>
 * The supplied transform is interpreted in the world coordinate system that the
 * wrapped source maps into via its {@link Source#getSourceTransform
 * getSourceTransform}. Since the images returned by {@link #getSource} and
 * {@@link #getInterpolatedSource} are expected to be in the scale levels' pixel
 * coordinates, this class internally maps pixel coordinate to world coordinates
 * before applying the provided transformation, then maps back to pixel
 * coordinates.
 * <p>
 * The bounding intervals for each mipmap level are estimated using the passed
 * {@code boundingBoxEstimator}, using {@link FacesSteps} with 10 steps as the
 * default.
 *
 * @param <T>
 *            the type of the original source
 */
public class RealTransformedSource<T> implements Source<T>, MipmapOrdering {
	protected final Source< T > source;

	/**
	 * optional new name. if null, the name of the original source will be used.
	 */
	protected final String name;

	/**
	 * This is either the {@link #source} itself, if it implements
	 * {@link MipmapOrdering}, or a {@link DefaultMipmapOrdering}.
	 */
	protected final MipmapOrdering sourceMipmapOrdering;

	/**
	 * The transformation to apply.
	 */
	protected InvertibleRealTransform transform;

	/**
	 * Pre-computed total transform, one per mipmap level. For each level, the
	 * sequence takes that level's pixel coordinates to world via the source
	 * transform, applies {@link #transform}, then maps back. Rebuilt whenever
	 * {@link #setTransform} is called.
	 */
	private List< InvertibleRealTransformSequence > transformSequences;

	/**
	 * Pre-computed bounding interval for {@link #getSource}, one per mipmap
	 * level. Rebuilt whenever {@link #setTransform} is called, using timepoint
	 * {@code 0}.
	 */
	private List< Interval > boundingIntervals;

	private final Supplier< Boolean > boundingBoxCullingSupplier;

	private final BiFunction< RealTransform, Interval, Interval > boundingBoxEstimator;

	/**
	 * Wraps {@code source} with the given world-coordinate {@code transform},
	 * using a default bounding-box estimator that samples the interval faces
	 * (10 steps per axis) and inherits bounding-box culling from {@code source}.
	 *
	 * @param source
	 *            the source to warp
	 * @param name
	 *            optional new name; if {@code null} the wrapped source's name is used
	 * @param transform
	 *            the transform to apply, in world coordinates
	 */
	public RealTransformedSource( final Source< T > source, final String name,
			final InvertibleRealTransform transform )
	{
		this( source, name, transform,
				(t, i) -> {
					return Intervals.smallestContainingInterval(
							t.boundingInterval(i, facesIsotropicSteps(i.numDimensions(), 10)));
				},
				null );
	}

	/**
	 * Wraps {@code source} with the given world-coordinate {@code transform},
	 * using the suppliued bounding-box estimator, and inherits bounding-box
	 * culling from {@code source}.
	 *
	 * @param source
	 *            the source to warp
	 * @param name
	 *            optional new name; if {@code null} the wrapped source's name
	 *            is used
	 * @param boundingBoxEstimator
	 *            estimates the world-space interval that {@link #getSource}
	 *            rasterizes over, given the transform and the wrapped interval
	 * @param transform
	 *            the transform to apply, in world coordinates
	 */
	public RealTransformedSource( final Source< T > source, final String name,
		    final BiFunction< RealTransform, Interval, Interval > boundingBoxEstimator,
			final InvertibleRealTransform transform )
	{
		this( source, name, transform, boundingBoxEstimator, null );
	}

	/**
	 * Wraps {@code source} with the given world-coordinate {@code transform}.
	 *
	 * @param source
	 *            the source to warp
	 * @param name
	 *            optional new name; if {@code null} the wrapped source's name is used
	 * @param transform
	 *            the transform to apply, in world coordinates
	 * @param boundingBoxEstimator
	 *            estimates the world-space interval that {@link #getSource}
	 *            rasterizes over, given the transform and the wrapped interval
	 * @param doBoundingBoxCulling
	 *            supplies the value returned by {@link #doBoundingBoxCulling()};
	 *            if {@code null}, the wrapped source's setting is used
	 */
	public RealTransformedSource( final Source< T > source, final String name,
		    final InvertibleRealTransform transform,
		    final BiFunction< RealTransform, Interval, Interval > boundingBoxEstimator,
			final Supplier< Boolean > doBoundingBoxCulling )
	{
		this.source = source;
		this.name = name;
		this.boundingBoxEstimator = boundingBoxEstimator;
		this.boundingBoxCullingSupplier = doBoundingBoxCulling;
		setTransform( transform );

		sourceMipmapOrdering = MipmapOrdering.class.isInstance( source ) ?
				( MipmapOrdering ) source : new DefaultMipmapOrdering( source );
	}

	@Override
	public boolean isPresent( final int t )
	{
		return source.isPresent( t );
	}

	@Override
	public boolean doBoundingBoxCulling()
	{
		if( boundingBoxCullingSupplier != null )
			return boundingBoxCullingSupplier.get();
		else
			return source.doBoundingBoxCulling();
	}

	/**
	 * Sets the world-coordinate transform to apply and (re)builds the per-mipmap-level
	 * caches derived from it.
	 * <p>
	 * For each level a total transform sequence is precomputed that maps that
	 * level's pixel coordinates to world via the source transform, applies a
	 * {@link InvertibleRealTransform#copy() copy} of {@code transform}, then maps
	 * back to pixels; a copy is used so each sequence has its own instance. The
	 * bounding interval that {@link #getSource} rasterizes over is estimated once
	 * per level (at timepoint {@code 0}) using the {@code boundingBoxEstimator}.
	 *
	 * @param transform
	 *            the transform to apply, in world coordinates
	 */
	public void setTransform( final InvertibleRealTransform transform )
	{
		this.transform = transform;

		final int numLevels = getNumMipmapLevels();
		final List< InvertibleRealTransformSequence > sequences = new ArrayList<>( numLevels );
		final List< Interval > intervals = new ArrayList<>( numLevels );
		for ( int level = 0; level < numLevels; level++ )
		{
			final AffineTransform3D affine = new AffineTransform3D();
			source.getSourceTransform( 0, level, affine );

			final InvertibleRealTransformSequence seq = new InvertibleRealTransformSequence();
			seq.add( affine );
			seq.add( transform.copy() );
			seq.add( affine.inverse() );
			sequences.add( seq );

			intervals.add( boundingBoxEstimator.apply( seq, source.getSource( 0, level ) ) );
		}
		transformSequences = sequences;
		boundingIntervals = intervals;
	}

	public Source< T > getWrappedSource()
	{
		return source;
	}

	@Override
	public RandomAccessibleInterval< T > getSource( final int t, final int level )
	{
		// TODO expose interp method - it probably matters
		final RealRandomAccessible<T> interpSrc = getInterpolatedSource( t, level, Interpolation.NEARESTNEIGHBOR );
		return Views.interval( Views.raster(interpSrc), boundingIntervals.get( level ) );
	}

	@Override
	public RealRandomAccessible< T > getInterpolatedSource( final int t, final int level, final Interpolation method )
	{
		final RealRandomAccessible<T> realSrc = source.getInterpolatedSource( t, level, method );
		return new RealTransformRealRandomAccessible< T, RealTransform >( realSrc, transformSequences.get( level ).inverse() );
	}

	@Override
	public synchronized void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		source.getSourceTransform( t, level, transform );
	}

	public InvertibleRealTransform getTransform()
	{
		return transform;
	}

	@Override
	public T getType()
	{
		return source.getType();
	}

	@Override
	public String getName()
	{
		if ( name != null ) return name;
		else return source.getName();
	}

	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return source.getVoxelDimensions();
	}

	@Override
	public int getNumMipmapLevels()
	{
		return source.getNumMipmapLevels();
	}

	@Override
	public synchronized MipmapHints getMipmapHints( final AffineTransform3D screenTransform, final int timepoint, final int previousTimepoint )
	{
		return sourceMipmapOrdering.getMipmapHints( screenTransform, timepoint, previousTimepoint );
	}

	private static IntervalSamplingMethod facesIsotropicSteps( int nd, long step)  {
		final long[] steps = new long[nd];
		Arrays.fill(steps, step);
		return new FacesSteps(steps);
	}

}
