JSON Website Structure Builder
-------------------------------------------------------

Builds a JSON file containing the structure of folders.
Except the root folder, every folder that appears in
the structure must contain a '.menu' file that consists
of the following two lines:
[Structure item name, single-line string]
[Structure item order, integer number]

-------------------------------------------------------

Usage: java -jar WebsiteStructureBuilder.jar [Root folder] [Output file]