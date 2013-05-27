public class Main {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println();
			System.out.println("JSON Website Structure Builder");
			System.out.println("------------------------------------------------------------");
			System.out.println("Usage: [Root folder] [Structure output file]");
			System.out.println("------------------------------------------------------------");
			System.out.println("Builds a JSON file containing the structure of folders");
			System.out.println("and also writes templated lists from the structure.");
			System.out.println();
			System.out.println("Except the root folder, every folder that appears in");
			System.out.println("the structure must contain a '.menu' file with a JSON");
			System.out.println("array describing the folder's index page and optionally");
			System.out.println("child elements.");
			System.out.println();
			System.out.println("Example:");
			System.out.println("[");
			System.out.println(" {\"name_de\":\"Startseite\", \"name_en\":\"Home\",");
			System.out.println("  \"order\":0},");
			System.out.println(" {\"link\":\"contact/impressum.html\", \"name_de\":\"Kontakt\",");
			System.out.println("  \"name_en\":\"Contact\", \"order\":80},");
			System.out.println("]");
			System.out.println("The 'order' attribute defines the order in which the items");
			System.out.println("appear in the menu and other outputs. Furthermore, the");
			System.out.println("'group' attribute can be provided.");
			System.out.println("Templates for groups may be defined in files named");
			System.out.println("'.template.your.group.name' and are valid for items with");
			System.out.println("the provided group name in sub folders of the template's");
			System.out.println("folder.");
			System.out.println();
			System.out.println("Example:");
			System.out.println("{");
			System.out.println(" \"item_template\":\"<b>{{name_de}}</b>\",");
			System.out.println(" \"item_spacer\":  \", \"");
			System.out.println("}");
			System.out.println("Placeholders contained in double curly braces are replaced");
			System.out.println("with attributes from the items' '.menu' files.");
			System.out.println();
			return;
		}

		StructureBuilder builder = new StructureBuilder(args[0], args[1]);
		builder.build();
	}
}
