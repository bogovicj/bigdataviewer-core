/*
 * #%L
 * BigDataViewer core classes with minimal dependencies
 * %%
 * Copyright (C) 2012 - 2016 Tobias Pietzsch, Stephan Saalfeld, Stephan Preibisch,
 * Jean-Yves Tinevez, HongKee Moon, Johannes Schindelin, Curtis Rueden, John Bogovic
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
package bdv.tools.brightness;

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import bdv.util.BoundedValue;
import bdv.util.BoundedValueDouble;

/**
 * A {@link JSlider} with a {@link JSpinner} next to it, both modifying the same
 * {@link BoundedValue value}.
 */
public class SliderPanel extends JPanel implements BoundedValueDouble.UpdateListener
{
	private static final long serialVersionUID = 6444334522127424416L;

	private final JSliderDouble slider;

	private final JSpinner spinner;

	private final BoundedValueDouble model;

	/**
	 * Create a {@link SliderPanel} to modify a given {@link BoundedValue value}.
	 *
	 * @param name
	 *            label to show next to the slider.
	 * @param model
	 *            the value that is modified.
	 */
	public SliderPanel( final String name, final BoundedValueDouble model, final double spinnerStepSize )
	{
		super();
		setLayout( new BorderLayout( 10, 10 ) );

		slider = new JSliderDouble( SwingConstants.HORIZONTAL, model.getRangeMin(), model.getRangeMax(), model.getCurrentValue() );
		spinner = new JSpinner();
		spinner.setModel( new SpinnerNumberModel( model.getCurrentValue(), model.getRangeMin(), model.getRangeMax(), spinnerStepSize ) );

		slider.addChangeListener( new ChangeListener()
		{
			@Override
			public void stateChanged( final ChangeEvent e )
			{
				final int value = slider.getValue();
				model.setCurrentValue( value );
			}
		} );

		spinner.addChangeListener( new ChangeListener()
		{
			@Override
			public void stateChanged( final ChangeEvent e )
			{
				final double value = (( Double )spinner.getValue()).doubleValue();
				model.setCurrentValue( value );
			}
		} );

		if ( name != null )
		{
			final JLabel label = new JLabel( name, SwingConstants.CENTER );
			label.setAlignmentX( Component.CENTER_ALIGNMENT );
			add( label, BorderLayout.WEST );
		}

		add( slider, BorderLayout.CENTER );
		add( spinner, BorderLayout.EAST );

		this.model = model;
		model.setUpdateListener( this );
	}

	public void setNumColummns( final int cols )
	{
		( ( JSpinner.NumberEditor ) spinner.getEditor() ).getTextField().setColumns( cols );
	}

	@Override
	public void update()
	{
		final double value = model.getCurrentValue();
		final double min = model.getRangeMin();
		final double max = model.getRangeMax();
		if (slider.getMaximum() != max || slider.getMinimum() != min)
		{
			slider.setMinimum( min );
			slider.setMaximum( max );
			final SpinnerNumberModel spinnerModel = ( SpinnerNumberModel ) spinner.getModel();
			spinnerModel.setMinimum( min );
			spinnerModel.setMaximum( max );
		}
		slider.setValue( value );
		spinner.setValue( value );
	}

	public String toString()
	{
		final double value = model.getCurrentValue();
		final double min = model.getRangeMin();
		final double max = model.getRangeMax();
		return "SliderPanel: [ " + min + " " + max + " ] : " + value;
	}

	public static class JSliderDouble extends JSlider
	{
		private static final long serialVersionUID = 4058697450156361851L;

		double resolution;
		double min, max, value;

		public JSliderDouble( int orientation, double min, double max, double value,
				double resolution )
		{
			super( orientation );
			this.resolution = resolution;
			this.min = min;
			this.max = max;
			this.value = value;
			super.setMinimum( 0 );
			update();
		}

		public JSliderDouble( int orientation, double min, double max, double value )
		{
			this( orientation, min, max, value, 0.1 );
		}

		public double getMinimumDouble()
		{
			return min;
		}

		public double getMaximumDouble()
		{
			return max;
		}

		public double getValueDouble()
		{
			return min + super.getValue() * resolution;
		}

		public void setMinimum( double minVal )
		{
			this.min = minVal;
			update();
		}

		public void setMaximum( double maxVal )
		{
			this.max = maxVal;
			update();
		}

		public void setValue( double val )
		{
			this.value = val;
			update();
		}

		public void setResolution( double res )
		{
			this.resolution = res;
			update();
		}

		private void update()
		{
			super.setMaximum( (int) ((max - min) / resolution) );
			super.setValue( (int) ((value - min) / resolution) );
		}

		@Override
		public void setMinimum( int minVal )
		{
			super.setMinimum( minVal );
		}

		@Override
		public void setMaximum( int minVal )
		{
			super.setMaximum( minVal );
		}

		@Override
		public void setValue( int minVal )
		{
			super.setValue( minVal );
		}

		public String toString()
		{
			return String.format( "JSliderDouble: [ %f : %f : %f ] : %f\n"
					+ "Slider [ %d, %d ] : %d", min, max, resolution, getValueDouble(),
					super.getMinimum(), super.getMaximum(), super.getValue() );
		}
	}

	public static void main( String[] args )
	{
		JSliderDouble a = new JSliderDouble( SwingConstants.HORIZONTAL, 0.0, 1.0, 0.5,
				0.1 );

		// a.addChangeListener( new ChangeListener()
		// {
		// @Override
		// public void stateChanged( ChangeEvent e )
		// {
		// System.out.println( a );
		// }
		// });

		// System.out.println( a );
		a.setMinimum( -1.0 );
		// System.out.println( a );

		JFrame frame = new JFrame( "SliderDemo" );
		frame.add( a );
		frame.pack();
		frame.setVisible( true );
	}
}
