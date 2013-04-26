public class Main {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println();
			System.out.println("JSON Website Structure Builder");
			System.out
					.println("-------------------------------------------------------");
			System.out.println("Usage: [Root folder] [Output file]");
			System.out
					.println("-------------------------------------------------------");
			System.out
					.println("Builds a JSON file containing the structure of folders.");
			System.out
					.println("Except the root folder, every folder that appears in");
			System.out
					.println("the structure must contain a '.menu' file that consists");
			System.out.println("of the following two lines:");
			System.out.println("[Structure item name, single-line string]");
			System.out.println("[Structure item order, integer number]");
			System.out.println();
			return;
		}

		StructureBuilder builder = new StructureBuilder(args[0], args[1]);
		builder.build();
	}
}
