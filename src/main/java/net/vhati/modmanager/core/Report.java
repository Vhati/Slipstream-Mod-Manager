package net.vhati.modmanager.core;

import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A list of messages, with a boolean outcome.
 */
public class Report {
	public final List<ReportMessage> messages;
	public final boolean outcome;

	public Report( List<ReportMessage> messages, boolean outcome ) {
		this.outcome = outcome;

		List<ReportMessage> tmpList = new ArrayList<ReportMessage>( messages );
		this.messages = Collections.unmodifiableList( tmpList );
	}



	/**
	 * Formats ReportMessages.
	 *
	 * Symbols are prepended to indicate type.
	 *
	 * Nested messages are indented.
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
				default: return getIndent();
			}
		}

		/**
		 * Returns the given CharSequence, or a new one decorated for the type.
		 */
		public CharSequence getDecoratedText( int messageType, CharSequence text ) {
			// Sections get underlined.
			if ( messageType == ReportMessage.SECTION ) {
				StringBuilder buf = new StringBuilder( text.length()*2+1 );
				buf.append( text ).append( "\n" );

				buf.append( getPrefix( messageType ).replaceAll( "\\S", " " ) );
				for ( int i=0; i < text.length(); i++ )
					buf.append( "-" );
				return buf;
			}
			else {
				return text;
			}
		}

		/**
		 * Formats a list of messages.
		 *
		 * Leading newlines in the first message will be omitted.
		 */
		public void format( List<ReportMessage> messages, Appendable buf, int indentCount ) throws IOException {
			for ( int i=0; i < messages.size(); i++ ) {
				ReportMessage message = messages.get( i );
				if ( i == 0 ) {
					// Skip leading newlines in first message.
					int start = 0;
					while ( start < message.text.length() && message.text.charAt(start) == '\n' )
						start++;
					if ( start > 0 ) {
						// Create a substitute message without them.
						CharSequence newText = message.text.subSequence( start, message.text.length() );
						message = new ReportMessage( message.type, newText, message.nestedMessages );
					}
				}
				format( message, buf, indentCount );
			}
		}

		/**
		 * Indents and decorates a message, then formats any nested messages.
		 */
		public void format( ReportMessage message, Appendable buf, int indentCount ) throws IOException {
			// Subsections get an extra linebreak above them.
			switch ( message.type ) {
				case ReportMessage.SUBSECTION:
				case ReportMessage.WARNING_SUBSECTION:
				case ReportMessage.ERROR_SUBSECTION:
					buf.append( "\n" );
				default:
					// Not a subsection.
			}

			CharSequence seq = getDecoratedText( message.type, message.text );

			// Indent the first line.
			for ( int i=0; i < indentCount; i++ )
				buf.append( getIndent() );
			buf.append( getPrefix( message.type ) );

			// Indent multi-line message text.
			Matcher m = breakPtn.matcher( seq );
			int lastEnd = 0;
			while ( m.find() ) {
				if ( m.start() - lastEnd > 0 )
					buf.append( seq.subSequence( lastEnd, m.start() ) );

				if ( m.group(1).length() > 0 ) {
					// At \n, instead of 0-length beginning (^).
					buf.append( "\n" );
					for ( int i=0; i < indentCount; i++ ) {
						buf.append( getIndent() );
					}
				}
				lastEnd = m.end();
			}
			int srcLen = seq.length();
			if ( lastEnd < srcLen )
				buf.append( seq.subSequence( lastEnd, srcLen ) );

			buf.append( "\n" );

			if ( message.nestedMessages != null ) {
				format( message.nestedMessages, buf, indentCount+1 );
			}
		}

		/** Exception-swallowing wrapper. */
		public void format( List<ReportMessage> messages, StringBuffer buf, int indentCount ) {
			try { format( messages, (Appendable)buf, indentCount ); }
			catch( IOException e ) {}
		}

		/** Exception-swallowing wrapper. */
		public void format( List<ReportMessage> messages, StringBuilder buf, int indentCount ) {
			try { format( messages, (Appendable)buf, indentCount ); }
			catch( IOException e ) {}
		}

		/** Exception-swallowing wrapper. */
		public void format( ReportMessage message, StringBuffer buf, int indentCount ) {
			try { format( message, (Appendable)buf, indentCount ); }
			catch( IOException e ) {}
		}

		/** Exception-swallowing wrapper. */
		public void format( ReportMessage message, StringBuilder buf, int indentCount ) {
			try { format( message, (Appendable)buf, indentCount ); }
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

		public final int type;
		public final CharSequence text;
		public final List<ReportMessage> nestedMessages;

		public ReportMessage( int type, CharSequence text ) {
			this( type, text, new ArrayList() );
		}

		public ReportMessage( int type, CharSequence text, List<ReportMessage> nestedMessages ) {
			this.type = type;
			this.text = text;
			List<ReportMessage> tmpList = new ArrayList<ReportMessage>( nestedMessages );
			this.nestedMessages = Collections.unmodifiableList( tmpList );
		}

		@Override
		public boolean equals( Object o ) {
			if ( o == null ) return false;
			if ( o == this ) return true;
			if ( o instanceof ReportMessage == false ) return false;
			ReportMessage other = (ReportMessage)o;
			if ( this.type != other.type ) return false;
			if ( !this.text.equals(other.text) ) return false;

			if ( this.nestedMessages == null ) {
				if ( other.nestedMessages != null )
					return false;
			} else {
				if ( !this.nestedMessages.equals( other.nestedMessages ) )
					return false;
			}
			return true;
		}

		@Override
		public int hashCode() {
			int result = 236;
			int salt = 778;
			int nullCode = 99;

			result = salt * result + this.type;
			result = salt * result + text.hashCode();

			if ( this.nestedMessages != null )
				result = salt * result + this.nestedMessages.hashCode();
			else
				result = salt * result + nullCode;

			return result;
		}
	}
}
