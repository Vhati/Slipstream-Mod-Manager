Mod Developer Notes

Creating an .ftl File

  An .ftl file is simply a renamed .zip with a specific file structure.
  For an example, try renaming and unpacking the example mods.

  The root of the ZIP file should contain one or more of these folders:
    data/
    audio/
    fonts/
    img/
    mod-appendix/

  You should ONLY put in the files that you want to modify. This keeps
  mod sizes low and prevents major conflict between mods.

  The "mod-appendix/" folder is for extra files that will not be inserted
  into the game's resources. Slipstream will look for the following inside.

    metadata.xml
      Optional embedded description. (See the example mods.)


The Append Extension

  Any file in your .ftl with the extension .xml.append will be appended to
  its respective vanilla file. (See the example mods.)

  It is highly recommended that you take advantage of this as much as
  possible. As a rule of thumb, if you're editing an event xml file,
  you're going to want to append your changes rather then flat out replace
  the file. Using .append helps prevent mod conflict.

  Keep in mind that you can override vanilla events (among other things)
  to your pleasure by writing an event of the same name. Whenever multiple
  tags share the same name, only the last one counts.

  When you're not overriding something, try to use unique names, so that
  it won't clobber another mod and vice versa.

  FTL 1.6.1 introduced <FTL>...</FTL> root tags wrapping XML files. If present,
  Slipstream will remove them, append, and put them back afterward. Special
  tags (see "Advanced XML" below) will be unaware of them. Mod files are not
  required to include these root tags, though they can. Slipstream will remove
  those as well.


General

  When developing a mod, save your text files as ANSI/ASCII, or UTF-8.
  Slipstream will tolerate UTF-16 and Windows-1252 ANSI.

  Dos style (CR-LF) line endings are preferred. The game only partially
  accepts the unix style (LF): fine for xml, crashing for layout.txt.
  Slipstream will convert both to CR-LF as it patches.

  File and directory names must be plain ASCII (no accents). That
  restriction isn't confirmed for the game, but the mod manager enforces
  it just to be safe.

  Images should be 32bit PNGs (24bit color + 8bit alpha transparency).
  Things that *should* be opaque rectangles like backgrounds may vary,
  but that would be undesirable for ship floors that should reveal the hull
  under them.


Mac-Specific

  OSX adds a junk to .zip files.
    These commands will address that:
      zip -d mymod.zip __MACOSX/\*
      zip -d mymod.zip \*.DS_Store


Encoding!?

  Text ultimately boils down to 1's and 0's. There are numerous standards
  for encoding that information. If an app reads the 1's and 0's assuming
  the wrong standard, it can come out as gibberish.

  ANSI - A family of related standards, often incompatible because each has
  language-specific characters and lacks others. They can at least agree on
  certain characters, called ASCII. When only ASCII characters are present,
  it doesn't matter which ANSI encoding was used.

  ASCII - abcdefghijklmnopqrstuvwxyz0123456789 (and uppercase)
          !"#$%&'()*+,-./:;<=>?@[\]^_`{|}~

  Windows-1252 - The most popular flavor of ANSI.

  Unicode - A series of standards, all capable of handling the same huge
  pool of characters, each successively less optimized for common
  characters in favor of consistency. Sometimes they come with a BOM
  header, a distinctive binary blob, that indicates what encoding was
  used.

  UTF-8 - A flavor of Unicode. For ASCII characters, it is identical
  with all the ANSI encodings. The BOM is optional. Apps often fairly
  safely assume all text is UTF-8 without a BOM. However, some apps may
  not know what to do when they see BOM bytes (eek weird binary), and if
  the document WERE written in ANSI with characters beyond ASCII, they
  may get garbled.

  UTF-16 - Windows uses this sometimes. Ideally always has a BOM. It is not
  identical with any other encodings. The whole thing looks like a mess
  when decoded incorrectly. Apps have to deliberately support it - usually
  by including tests to determine when they're dealing with UTF-16 or
  something else.


Advanced XML

  Since v1.2, Slipstream supports special tags to not only append, but
  insert and edit existing XML. You can practice using them with the
  "XML Sandbox", under the File menu.

  They take the form:

    <mod:find... reverse="false" start="0" limit="-1" panic="false">
      <mod:holderForExtraFindArgs />
      <mod:someCommand />
      <mod:someCommand />
    </mod:find...>

  Some identify existing tags, using each result as context for commands.

    Unless stated otherwise, these all accept optional reverse, start,
    limit, and panic args: defaulting to search forward, skip 0 matched
    candidates, return up to an unlimited number of results, and not cause
    an error when no results are found.
    Sometimes the <find...> may have an auxiliary tag just to hold more
    args.

    <mod:findName type="abc" name="def">
    </mod:findName>

      Searches for tags of a given type with the given name attribute.
      The type arg is optional.
      Its unusual defaults are: reverse="true", start="0", limit="1".
      It finds the first match from the end.

    <mod:findLike type="abc">
      <mod:selector a="1" b="2">abc</mod:selector>
    </mod:findLike>

      Searches for tags of a given type, with all of the given attributes
      and the given value. All of these find arguments are optional. To
      omit the value, leave it blank, or make <selector /> self-closing.
      If no value or attributes are given, <selector> is unnecessary.

    <mod:findWithChildLike type="abc" child-type="def">
      <mod:selector a="1" b="2" ...>abc</mod:selector>
    </mod:findWithChildLike>

      As <findLike>, except it searches for tags of a given type, that
      contain certain children with the attributes and value. All args are
      optional here as well. Note: The children are only search criteria,
      not results themselves.

    <mod:findComposite>
      <mod:par op="AND">
        <mod:find...>
        <mod:find...>
        <mod:find...>
      </mod:par>
    </mod:findComposite>

      Collates results from several <find...> criteria, or even multiple
      nested <par>entheses. The <par> combines results using "OR" (union)
      or "AND" (intersection) logic. Any commands within those <find...>
      tags will be ignored.


  The following commands can occur inside a <find...>.

    <mod:find...>
      Searches the context tag's children and acts on them with its own
      nested commands.

    <mod:setValue>abc</mod:setValue>
      Sets a text value for the context tag.

    <mod:setAttributes a="1" b="2" />
      Sets/adds one or more attributes on the context tag.

    <mod:removeAttributes a="" b="" />
      Removes one or more attributes on the context tag (the values here
      will be ignored). This was introduced in SMM 1.7.

    <mod:removeTag />
      Removes the context tag entirely.

    <mod-append:XYZ>
    </mod-append:XYZ>
      Appends a new <XYZ> child to the context tag. Aside from the prefix,
      the tag's type and content will appear as-is. It can be self-closing.

    <mod-overwrite:XYZ>
    </mod-overwrite:XYZ>
      If possible, the first <XYZ> child under the context tag will be
      removed, and this <XYZ> will be inserted in its place. Otherwise,
      this has the same effect as <mod-append:XYZ>.

  Special tags and normal append content are processed in the order they
  occur in your mod. And when patching several mods at once, later mods
  edit in the wake of earlier ones.


Raw XML

  This feature was a workaround for a bug that was fixed in Slipstream 1.7.

  The bug caused Slipstream to trim off leading/trailing space in XML values.
  This was problematic for "misc.xml" (in FTL 1.5.4-1.5.13), which defined
  phrases for localization. Some phrases had spaces, which led to ugly results
  in-game if trimmed.

  FTL is quirky. Occasionally you may need to include non-standard XML in a
  mod without elaborate parsing.

  If your mod has a file named "misc.xml.rawappend", the content of that
  file will be tacked onto the end of "misc.xml". Line-endings and encoding
  will be standardized. Root <FTL> tags, if present, will be removed before
  appending and restored after, but Slipstream will make no attempt to
  (mis)understand the tags of either file for advanced modding.

  You can still override existing tags by adding your own with the same
  'name' attribute, since FTL honors the last it sees.

  Similarly a file named "misc.xml.rawclobber" will entirely replace the
  original "misc.xml".

  Any other mods patched afterward must either avoid that file or also treat
  it as raw themselves. Hence this should be used as a last resort.


Commandline

  Running Slipstream from a prompt can speed up development...

  --patch Abc.ftl Def "Ghi 1.0.ftl"

    Patches named mod files. Dirs can also be named, so you won't have to
    re-zip for every test.

  --global-panic

    While patching, reveals typoed <find...> tags. Any find that yields no
    matches will cause an error, as if it had panic='true'.

  --runftl

    Runs the game. If used with "--patch", runs afterward if successful.

  --validate Abc

    Prints a "Validate" report for named mod files/dirs. Handy when
    you're checking repeatedly as you correct typos.


Pitfalls

  FTL Bug (fixed in 1.03.3): If a ship is modded to have level 5 shields,
  asteroid storms will be abnormally fast.
  http://www.ftlgame.com/forum/viewtopic.php?f=9&t=11057

  The game will crash at the main menu or hangar if an event choice loads
  another event, which has a choice that loads the previous event. FTL
  does not like event loops.
  http://www.ftlgame.com/forum/viewtopic.php?f=12&t=12265

  When adding a music track to sounds.xml, the explore and battle theme
  files are played simultaneously as one song (mixing between them when
  entering/exiting combat). They should have similar duration because if one
  is longer than the other, there may be noticeable silence at the end of the
  shorter piece.
  http://www.ftlgame.com/forum/viewtopic.php?f=12&t=9111
