package net.vhati.modmanager.core;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A human-readable block of text, with a boolean outcome.
 */
public class Report {
	public final CharSequence text;
	public final boolean outcome;

	public Report( CharSequence text, boolean outcome ) {
		this.text = text;
		this.outcome = outcome;
	}



	/**
	 * Formats ReportMessages to include in buffered reports.
	 *
	 * Symbols are prepended to indicate type.
	 *
	 * Methods can accept a formatter as an argument,
	 * internally accumulate messages, format them,
	 * and return an Appendable CharSequence.
	 *
	 * To nest reports, that buffer can be intented
	 * and appended to another buffer; or it can be
	 * wrapped in a NESTED_BLOCK message of its own.
	 *
	 * The Appendable interface claims to throw
	 * IOException, but StringBuffer and StringBuilder
	 * never do. So extra methods specifically accept
	 * those classes and swallow the exception.
	 *
	 * If exceptions are desired, cast args to the
	 * more general type.
	 */
	public static class ReportFormatter {
		protected Pattern breakPtn = Pattern.compile( "(^|\n)(?=[^\n])" );

		public String getIndent() { return "  "; }

		public String getPrefix( int messageType ) {
			switch ( messageType ) {
				case ReportMessage.WARNING:            return "~ ";
				case ReportMessage.ERROR:              return "! ";
				case ReportMessage.EXCEPTION:          return "! ";
				case ReportMessage.SECTION :           return "@ ";
				case ReportMessage.SUBSECTION:         return "> ";
				case ReportMessage.WARNING_SUBSECTION: return "~ ";
				case ReportMessage.ERROR_SUBSECTION:   return "! ";
				case ReportMessage.NESTED_BLOCK:       return "";
				default: return getIndent();
			}
		}

		public void format( List<ReportMessage> messages, Appendable buf ) throws IOException {
			for ( ReportMessage message : messages )
				format( message, buf );
		}

		public void format( ReportMessage message, Appendable buf ) throws IOException {
			if ( message.type == ReportMessage.NESTED_BLOCK ) {
				// Already formatted this once, indent it instead.

				// Skip leading newlines
				int start = 0;
				while ( start < message.text.length() && message.text.charAt(start) == '\n' )
					start++;
				if ( start > 0 )
					indent( message.text.subSequence( start, message.text.length() ), buf );
				else
					indent( message.text, buf );
				return;
			}

			// Subsections get an extra linebreak above them.
			switch ( message.type ) {
				case ReportMessage.SUBSECTION:
				case ReportMessage.WARNING_SUBSECTION:
				case ReportMessage.ERROR_SUBSECTION:
					buf.append( "\n" );
				default:
					// Not a subsection.
			}

			buf.append( getPrefix( message.type ) );
			buf.append( message.text );
			buf.append( "\n" );

			// Sections get underlined.
			if ( message.type == ReportMessage.SECTION ) {
				buf.append( getIndent() );
				for ( int i=0; i < message.text.length(); i++ )
					buf.append( "-" );
				buf.append( "\n" );
			}
		}

		public void indent( CharSequence src, Appendable dst ) throws IOException {
			Matcher m = breakPtn.matcher( src );
			int lastEnd = 0;
			while ( m.find() ) {
				if ( m.start() - lastEnd > 0 )
					dst.append( src.subSequence( lastEnd, m.start() ) );

				if ( m.group(1).length() > 0 )  // Didn't match beginning (^).
					dst.append( "\n" );
				dst.append( getIndent() );
				lastEnd = m.end();
			}
			int srcLen = src.length();
			if ( lastEnd < srcLen )
				dst.append( src.subSequence( lastEnd, srcLen ) );
		}

		/** Exception-swallowing wrapper. */
		public void format( List<ReportMessage> messages, StringBuffer buf ) {
			try { format( messages, (Appendable)buf ); }
			catch( IOException e ) {}
		}

		/** Exception-swallowing wrapper. */
		public void format( List<ReportMessage> messages, StringBuilder buf ) {
			try { format( messages, (Appendable)buf ); }
			catch( IOException e ) {}
		}

		/** Exception-swallowing wrapper. */
		public void format( ReportMessage message, StringBuffer buf ) {
			try { format( message, (Appendable)buf ); }
			catch( IOException e ) {}
		}

		/** Exception-swallowing wrapper. */
		public void format( ReportMessage message, StringBuilder buf ) {
			try { format( message, (Appendable)buf ); }
			catch( IOException e ) {}
		}

		/** Exception-swallowing wrapper. */
		public void indent( CharSequence src, StringBuffer dst ) {
			try { indent( src, (Appendable)dst ); }
			catch( IOException e ) {}
		}

		/** Exception-swallowing wrapper. */
		public void indent( CharSequence src, StringBuilder dst ) {
			try { indent( src, (Appendable)dst ); }
			catch( IOException e ) {}
		}
	}



	/**
	 * Notice text, with a formatting hint.
	 *
	 * Messages can be compared for equality
	 * to ignore repeats.
	 */
	public static class ReportMessage {
		public static final int INFO = 0;
		public static final int WARNING = 1;
		public static final int ERROR = 2;
		public static final int EXCEPTION = 3;
		public static final int SECTION = 4;
		public static final int SUBSECTION = 5;
		public static final int WARNING_SUBSECTION = 6;
		public static final int ERROR_SUBSECTION = 7;
		public static final int NESTED_BLOCK = 8;

		public final int type;
		public final CharSequence text;

		public ReportMessage( int type, CharSequence text ) {
			this.type = type;
			this.text = text;
		}

		@Override
		public boolean equals( Object o ) {
			if ( o == null ) return false;
			if ( o == this ) return true;
			if ( o instanceof ReportMessage == false ) return false;
			ReportMessage other = (ReportMessage)o;
			return ( this.type == other.type && this.text.equals(other.text) );
		}

		@Override
		public int hashCode() {
			int result = 236;
			int salt = 778;

			result = salt * result + this.type;
			result = salt * result + text.hashCode();
			return result;
		}
	}
}
