/*
 * #%L
 * BigDataViewer core classes with minimal dependencies
 * %%
 * Copyright (C) 2012 - 2015 BigDataViewer authors
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
package bdv.tools.boundingbox;

import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import bdv.tools.brightness.SliderPanel;
import bdv.util.BoundedRealInterval;
import bdv.util.ModifiableInterval;
import bdv.util.ModifiableRealInterval;

// a JPanel containing X,Y,Z min/max sliders for adjusting an interval
public class BoxSelectionPanel extends JPanel
{
	public static interface SelectionUpdateListener
	{
		public void selectionUpdated();
	}

	private static final long serialVersionUID = 1L;

	private final BoundedRealInterval[] ranges;

	private final ModifiableRealInterval selection;

	private final ArrayList< SelectionUpdateListener > listeners;

	public BoxSelectionPanel( final ModifiableInterval selection, final RealInterval rangeInterval )
	{
		this( new ModifiableRealInterval( selection ), rangeInterval );
	}

	public BoxSelectionPanel( final ModifiableRealInterval selection, final RealInterval rangeInterval )
	{
		final int n = selection.numDimensions();
		this.selection = selection;
		ranges = new BoundedRealInterval[ n ];
		listeners = new ArrayList< SelectionUpdateListener >();

		setLayout( new BoxLayout( this, BoxLayout.PAGE_AXIS ) );
		for ( int d = 0; d < n; ++d )
		{
			final double rangeMin = rangeInterval.realMin( d );
			final double rangeMax = rangeInterval.realMax( d );
			final double initialMin = Math.max( selection.realMin( d ), rangeMin );
			final double initialMax = Math.min( selection.realMax( d ), rangeMax );
			final BoundedRealInterval range = new BoundedRealInterval( rangeMin, rangeMax, initialMin, initialMax, 1 )
			{
				@Override
				protected void updateInterval( final double min, final double max )
				{
					updateSelection();
				}
			};
			final JPanel sliders = new JPanel();
			sliders.setLayout( new BoxLayout( sliders, BoxLayout.PAGE_AXIS ) );
			final String axis = ( d == 0 ) ? "x" : ( d == 1 ) ? "y" : "z";
			final SliderPanel minPanel = new SliderPanel( axis + " min", range.getMinBoundedValue(), 1 );
			minPanel.setBorder( BorderFactory.createEmptyBorder( 0, 10, 10, 10 ) );
			sliders.add( minPanel );
			final SliderPanel maxPanel = new SliderPanel( axis + " max", range.getMaxBoundedValue(), 1 );
			maxPanel.setBorder( BorderFactory.createEmptyBorder( 0, 10, 10, 10 ) );
			sliders.add( maxPanel );
			add( sliders );
			ranges[ d ] = range;
		}
	}

	public void setBoundsInterval( final Interval interval )
	{
		final int n = selection.numDimensions();
		for ( int d = 0; d < n; ++d )
			ranges[ d ].setRange( ( int ) interval.min( d ), ( int ) interval.max( d ) );
	}

	public void addSelectionUpdateListener( final SelectionUpdateListener l )
	{
		listeners.add( l );
	}

	public void updateSelection()
	{
		final int n = selection.numDimensions();
		final double[] min = new double[ n ];
		final double[] max = new double[ n ];
		for ( int d = 0; d < n; ++d )
		{
			min[ d ] = ranges[ d ].getMinBoundedValue().getCurrentValue();
			max[ d ] = ranges[ d ].getMaxBoundedValue().getCurrentValue();
		}
		selection.set( new FinalRealInterval( min, max ) );
		for ( final SelectionUpdateListener l : listeners )
			l.selectionUpdated();
	}

	public void updateSliders( final Interval interval )
	{
		final int n = selection.numDimensions();
		if ( interval.numDimensions() != n )
			throw new IllegalArgumentException();
		final long[] min = new long[ n ];
		final long[] max = new long[ n ];
		interval.min( min );
		interval.max( max );
		for ( int d = 0; d < n; ++d )
		{
			ranges[ d ].getMinBoundedValue().setCurrentValue( ( int ) min[ d ] );
			ranges[ d ].getMaxBoundedValue().setCurrentValue( ( int ) max[ d ] );
		}
	}
}
