
JSON Website Structure Builder
------------------------------------------------------------
Usage: [Root folder] [Structure output file]
------------------------------------------------------------
Builds a JSON file containing the structure of folders
and also writes templated lists from the structure.

Except the root folder, every folder that appears in
the structure must contain a '.menu' file with a JSON
array describing the folder's index page and optionally
child elements.

Example:
[
 {"name_de":"Startseite", "name_en":"Home",
  "order":0},
 {"link":"contact/impressum.html", "name_de":"Kontakt",
  "name_en":"Contact", "order":80},
]
The 'order' attribute defines the order in which the items
appear in the menu and other outputs. Furthermore, the
'group' attribute can be provided.
Templates for groups may be defined in files named
'.template.your.group.name' and are valid for items with
the provided group name in sub folders of the template's
folder.

Example:
{
 "item_template":"<b>{{name_de}}</b>",
 "item_spacer":  ", "
}
Placeholders contained in double curly braces are replaced
with attributes from the items' '.menu' files.


------------------------------------------------------------

Usage: java -jar WebsiteStructureBuilder.jar [Root folder] [Output file]

NOTE:
Library json-simple-1.1.1.jar must exist in folder
'WebsiteStructureBuilder_lib'. Please download it from
http://code.google.com/p/json-simple/