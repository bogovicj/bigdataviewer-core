package bdv.util;

import bdv.tools.transformation.TransformedSource;
import bdv.viewer.Source;
import net.imglib2.realtransform.AffineTransform3D;
import ucar.units.ConversionException;
import ucar.units.Factor;
import ucar.units.NoSuchUnitException;
import ucar.units.PrefixDBException;
import ucar.units.SpecificationException;
import ucar.units.StandardUnitFormat;
import ucar.units.Unit;
import ucar.units.UnitClassException;
import ucar.units.UnitDB;
import ucar.units.UnitDBException;
import ucar.units.UnitFormat;
import ucar.units.UnitFormatImpl;
import ucar.units.UnitFormatManager;
import ucar.units.UnitParseException;
import ucar.units.UnitSystemException;

public class Units
{
	protected static final UnitFormat UNIT = UnitFormatManager.instance();

	public static < T > Source< T > convertUnit( final Source< T > src, final String newUnit )
	{
		final String srcUnit = src.getVoxelDimensions().unit();
		if ( srcUnit.equals( newUnit ) )
			return src;

		try
		{
			final Unit currentUnit = UNIT.parse( srcUnit );
			final Unit destinationUnit = UNIT.parse( newUnit );
			updateTransform( src, unitTransform( currentUnit, destinationUnit ) );
		}
		catch ( SpecificationException | UnitDBException | PrefixDBException
				| UnitSystemException | ConversionException ignore )
		{}

		// something went wrong
		return src;
	}

	public static void updateTransform( final Source< ? > src, final AffineTransform3D transform )
	{
		if ( src instanceof TransformedSource )
		{
			final AffineTransform3D tmp = new AffineTransform3D();
			final TransformedSource< ? > ts = ( TransformedSource< ? > ) src;
			ts.getFixedTransform( tmp );
			tmp.preConcatenate( transform );
			ts.setFixedTransform( tmp );
		}
		else
		{
			System.err.println( "Could not update transform for source: " + src.getName() );
		}
	}

	public static AffineTransform3D unitTransform( final Unit srcUnit, final Unit dstUnit ) throws ConversionException
	{
		final AffineTransform3D out = new AffineTransform3D();
		if ( srcUnit.isCompatible( dstUnit ) )
		{
			final double scale = srcUnit.convertTo( 1.0, dstUnit );
			out.scale( scale );
		}
		return out;
	}

	/**
	 * A UnitFormat implementation that wraps the {@link StandardUnitFormat} but
	 * converts the symbol "µ" to a "u", which udunits handles as expected.
	 * <p>
	 * Note: I tried {@code PrefixDBManager.instance().addSymbol("μ", 1e-6);}
	 * but it it resulted in a
	 * {@code ucar.units.UnitParseException: Specificatioucar.units.UnitParseException: Specification error: µmn error: µm}
	 */
	public static class BdvUnitFormat extends UnitFormatImpl
	{
		private static BdvUnitFormat INSTANCE;

		private static final UnitFormat STANDARD_FORMAT = UnitFormatManager.instance();

		public static final String MU_PREFIX = "μ";

		public static final String MICRO_PREFIX = "µ";

		public static BdvUnitFormat instance()
		{
			if ( INSTANCE == null )
				return new BdvUnitFormat();
			else
				return INSTANCE;
		}

		@Override
		public Unit parse( String spec, UnitDB unitDB ) throws NoSuchUnitException, UnitParseException, SpecificationException, UnitDBException, PrefixDBException, UnitSystemException
		{
			return STANDARD_FORMAT.parse( preprocess( spec ), unitDB );
		}

		@Override
		public StringBuffer format( Factor factor, StringBuffer buffer )
		{
			return STANDARD_FORMAT.format( factor, preprocess( buffer ) );
		}

		@Override
		public StringBuffer format( Unit unit, StringBuffer buffer ) throws UnitClassException
		{
			return STANDARD_FORMAT.format( unit, preprocess( buffer ) );
		}

		@Override
		public StringBuffer longFormat( Unit unit, StringBuffer buffer ) throws UnitClassException
		{
			return STANDARD_FORMAT.longFormat( unit, preprocess( buffer ) );
		}

		public StringBuffer preprocess( final StringBuffer buffer )
		{
			return new StringBuffer( preprocess( buffer.toString() ) );
		}

		public String preprocess( final String spec )
		{
			return spec.replaceAll( MU_PREFIX, "u" ).replaceAll( MICRO_PREFIX, "u" );
		}

	}

}
