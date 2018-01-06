package net.vhati.modmanager.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A version string (eg, 10.4.2_17 or 2.7.5rc1 ).
 *
 * It is composed of three parts:
 * - A series of period-separated positive ints.
 *
 * - The numbers may be immediately followed by a short suffix string.
 *
 * - Finally, a string comment, separated from the rest by a space.
 *
 * The (numbers + suffix) or comment may appear alone.
 *
 * For details, see the string constructor and compareTo().
 */
public class ComparableVersion implements Comparable<ComparableVersion> {

	private Pattern numbersPtn = Pattern.compile( "^((?:\\d+[.])*\\d+)" );
	private Pattern suffixPtn = Pattern.compile( "([-_]|(?:[-_]?(?:[ab]|r|rc)))(\\d+)|([A-Za-z](?= |$))" );
	private Pattern commentPtn = Pattern.compile( "(.+)$" );

	private int[] numbers;
	private String suffix;
	private String comment;

	private String suffixDivider;  // Suffix prior to a number, if there was a number.
	private int suffixNum;


	public ComparableVersion( int[] numbers, String suffix, String comment ) {
		this.numbers = numbers;
		setSuffix( suffix );
		setComment( comment );
	}

	/**
	 * Constructs an AppVersion by parsing a string.
	 *
	 * The suffix can be:
	 * - A divider string followed by a number.
	 *   - Optional Hyphen/underscore, then a|b|r|rc, then 0-9+.
	 *   - Hyphen/underscore, then 0-9+.
	 * Or the suffix can be a single letter without a number.
	 *
	 * Examples:
	 *   1
	 *   1 Blah
	 *   1.2 Blah
	 *   1.2.3 Blah
	 *   1.2.3-8 Blah
	 *   1.2.3_b9 Blah
	 *   1.2.3a1 Blah
	 *   1.2.3b1 Blah
	 *   1.2.3rc2 Blah
	 *   1.2.3z Blah
	 *   1.2.3D
	 *   Alpha
	 *
	 * @throws IllegalArgumentException if the string is unsuitable
	 */
	public ComparableVersion( String s ) {
		boolean noNumbers = true;
		boolean noComment = true;

		Matcher numbersMatcher = numbersPtn.matcher( s );
		Matcher suffixMatcher = suffixPtn.matcher( s );
		Matcher commentMatcher = commentPtn.matcher( s );

		if ( numbersMatcher.lookingAt() ) {
			noNumbers = false;
			setNumbers( numbersMatcher.group( 0 ) );

			commentMatcher.region( numbersMatcher.end(), s.length() );

			// We have numbers; do we have a suffix?
			suffixMatcher.region( numbersMatcher.end(), s.length() );
			if ( suffixMatcher.lookingAt() ) {
				setSuffix( suffixMatcher.group( 0 ) );

				commentMatcher.region( suffixMatcher.end(), s.length() );
			}
			else {
				setSuffix( null );
			}

			// If a space occurs after (numbers +suffix?), skip it.
			// Thus the comment matcher will start on the first comment char.
			//
			if ( commentMatcher.regionStart()+1 < s.length() ) {
				if ( s.charAt( commentMatcher.regionStart() ) == ' ' ) {
					commentMatcher.region( commentMatcher.regionStart()+1, s.length() );
				}
			}
		}
		else {
			numbers = new int[0];
			setSuffix( null );
		}

		// Check for a comment (at the start, elsewhere if region was set).
		if ( commentMatcher.lookingAt() ) {
			noComment = false;
			setComment( commentMatcher.group( 1 ) );
		}

		if ( noNumbers && noComment ) {
			throw new IllegalArgumentException( "Could not parse version string: "+ s );
		}
	}


	private void setNumbers( String s ) {
		if ( s == null || s.length() == 0 ) {
			numbers = new int[0];
			return;
		}

		Matcher m = numbersPtn.matcher( s );
		if ( m.matches() ) {
			String numString = m.group( 1 );
			String[] numChunks = numString.split("[.]");

			numbers = new int[ numChunks.length ];
			for ( int i=0; i < numChunks.length; i++ ) {
				numbers[i] = Integer.parseInt( numChunks[i] );
			}
		}
		else {
			throw new IllegalArgumentException( "Could not parse version numbers string: "+ s );
		}
	}

	private void setSuffix( String s ) {
		if ( s == null || s.length() == 0 ) {
			suffix = null;
			suffixNum = -1;
			return;
		}

		Matcher m = suffixPtn.matcher( s );
		if ( m.matches() ) {
			// Matched groups 1 and 2... or 3.

			if ( m.group( 1 ) != null ) {
				suffixDivider = m.group( 1 );
			}

			if ( m.group( 2 ) != null ) {
				suffixNum = Integer.parseInt( m.group( 2 ) );
			} else {
				suffixNum = -1;
			}

			suffix = m.group( 0 );
		}
		else {
			throw new IllegalArgumentException( "Could not parse version suffix string: "+ s );
		}
	}

	private void setComment( String s ) {
		if ( s == null || s.length() == 0 ) {
			comment = null;
			return;
		}

		Matcher m = commentPtn.matcher( s );
		if ( m.matches() ) {
			comment = m.group( 1 );
		}
		else {
			throw new IllegalArgumentException( "Could not parse version comment string: "+ s );
		}
	}


	/**
	 * Returns the array of major/minor/etc version numbers.
	 */
	public int[] getNumbers() {
		return numbers;
	}

	/**
	 * Returns the pre-number portion of the suffix, or null if there was no number.
	 */
	public String getSuffixDivider() {
		return suffixDivider;
	}

	/**
	 * Returns the number in the suffix, or -1 if there was no number.
	 */
	public int getSuffixNumber() {
		return suffixNum;
	}

	/**
	 * Returns the entire suffix, or null.
	 */
	public String getSuffix() {
		return suffix;
	}

	/**
	 * Returns the human-readable comment, or null.
	 */
	public String getComment() {
		return comment;
	}


	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		for ( int number : numbers ) {
			if ( buf.length() > 0 ) buf.append( "." );
			buf.append( number );
		}
		if ( suffix != null ) {
			buf.append( suffix );
		}
		if ( comment != null ) {
			if ( buf.length() > 0 ) buf.append( " " );
			buf.append( comment );
		}

		return buf.toString();
	}


	/**
	 * Compares this object with the specified object for order.
	 *
	 * - The ints are compared arithmetically. In case of ties,
	 * the version with the most numbers wins.
	 * - If both versions' suffixes have a number, and the same
	 * characters appear before that number, then the suffix number
	 * is compared arithmetically.
	 * - Then the entire suffix is compared alphabetically.
	 * - Then the comment is compared alphabetically.
	 */
	@Override
	public int compareTo( ComparableVersion other ) {
		if ( other == null ) return -1;
		if ( other == this ) return 0;

		int[] oNumbers = other.getNumbers();
		for ( int i=0; i < numbers.length && i < oNumbers.length; i++ ) {
			if ( numbers[i] < oNumbers[i] ) return -1;
			if ( numbers[i] > oNumbers[i] ) return 1;
		}
		if ( numbers.length < oNumbers.length ) return -1;
		if ( numbers.length > oNumbers.length ) return 1;

		if ( suffixDivider != null && other.getSuffixDivider() != null ) {
			if ( suffixDivider.equals( other.getSuffixDivider() ) ) {
				if ( suffixNum < other.getSuffixNumber() ) return -1;
				if ( suffixNum > other.getSuffixNumber() ) return 1;
			}
		}

		if ( suffix == null && other.getSuffix() != null ) return -1;
		if ( suffix != null && other.getSuffix() == null ) return 1;
		if ( suffix != null && other.getSuffix() != null ) {
			int cmp = suffix.compareTo( other.getSuffix() );
			if ( cmp != 0 ) return cmp;
		}

		if ( comment == null && other.getComment() != null ) return -1;
		if ( comment != null && other.getComment() == null ) return 1;
		if ( comment != null && other.getComment() != null ) {
			int cmp = comment.compareTo( other.getComment() );
			if ( cmp != 0 ) return cmp;
		}

		return 0;
	}

	@Override
	public boolean equals( Object o ) {
		if ( o == null ) return false;
		if ( o == this ) return true;
		if ( o instanceof ComparableVersion == false ) return false;
		ComparableVersion other = (ComparableVersion)o;

		int[] oNumbers = other.getNumbers();
		for ( int i=0; i < numbers.length && i < oNumbers.length; i++ ) {
			if ( numbers[i] != oNumbers[i] ) return false;
		}
		if ( numbers.length != oNumbers.length ) return false;

		if ( suffix == null && other.getSuffix() != null ) return false;
		if ( suffix != null && other.getSuffix() == null ) return false;
		if ( !suffix.equals( other.getSuffix() ) ) return false;

		if ( comment == null && other.getComment() != null ) return false;
		if ( comment != null && other.getComment() == null ) return false;
		if ( !comment.equals( other.getComment() ) ) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = 79;
		int salt = 35;
		int nullCode = 13;

		List<Integer> tmpNumbers = new ArrayList<Integer>( getNumbers().length );
		for ( int n : getNumbers() )
			tmpNumbers.add( new Integer( n ) );
		result = salt * result + tmpNumbers.hashCode();

		String tmpSuffix = getSuffix();
		if ( tmpSuffix == null )
			result = salt * result + nullCode;
		else
			result = salt * result + tmpSuffix.hashCode();

		String tmpComment = getComment();
		if ( tmpComment == null )
			result = salt * result + nullCode;
		else
			result = salt * result + tmpComment.hashCode();

		return result;
	}
}
