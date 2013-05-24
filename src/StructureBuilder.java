import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class StructureBuilder {
	private String folderPath;
	private String outputFilePath;

	public StructureBuilder(String folderPath, String outputFilePath) {
		this.folderPath = folderPath;
		this.outputFilePath = outputFilePath;
	}

	public void build() {
		PriorityQueue<MenuItem> itemQueue = new PriorityQueue<StructureBuilder.MenuItem>();

		System.out.println("Parsing menu files ...");
		File folder = new File(folderPath);
		if (!folder.exists()) {
			System.out.println("Could not find path '" + folder.getAbsolutePath() + "'. Abort.");
			return;
		}
		int rootFolderPathLen = folder.getAbsolutePath().length();
		buildFolderItemQueue(folder, itemQueue, rootFolderPathLen);

		System.out.println("Writing menu structure file ...");
		try {
			OutputStreamWriter outputWriter = new OutputStreamWriter(new FileOutputStream(outputFilePath));
			try {
				JSONArray siteStructure = buildMenuStructure(itemQueue);
				outputWriter.write(siteStructure.toJSONString().replace("\"", "\\\""));
			} finally {
				outputWriter.close();
			}
		} catch (FileNotFoundException e) {
			System.out.println("Could not write output file '" + outputFilePath + '.');
			return;
		} catch (IOException e) {
			System.out.println("Could not write output file '" + outputFilePath + '.');
			return;
		}

		System.out.println("Done.");
	}

	private void buildFolderItemQueue(File folder, PriorityQueue<MenuItem> itemQueue, int rootFolderPathLen) {

		MenuItem siteMenuItem = null;
		Set<MenuItem> customSubItems = new HashSet<MenuItem>();

		// Read and parse menu file
		File menuFile = new File(folder.getAbsolutePath() + "/.menu");
		InputStreamReader menuFileReader;
		try {
			menuFileReader = new InputStreamReader(new FileInputStream(menuFile));
		} catch (FileNotFoundException e) {
			System.out.println("Could not find menu file '" + folder.getAbsolutePath() + "'. Skipping path.");
			return;
		}
		try {
			JSONArray menuItemArray;
			try {
				JSONParser parser = new JSONParser();
				Object parsedMenuFile = parser.parse(menuFileReader);
				if (!(parsedMenuFile instanceof JSONArray)) {
					System.out.println("Menu file " + menuFile.getAbsolutePath() + " does not contain a JSON array. Skipping path.");
					return;
				}
				menuItemArray = (JSONArray) parsedMenuFile;
			} catch (IOException e1) {
				System.out.println("Could not read menu file '" + folder.getAbsolutePath() + "'. Skipping path.");
				return;
			} catch (ParseException e) {
				System.out.println("Menu file '" + menuFile.getAbsolutePath() + "' does not contain a valid JSON array. " + e.toString()
						+ " Skipping path.");
				return;
			}

			for (Object o : menuItemArray) {
				if (!(o instanceof JSONObject)) {
					System.out.println("Found invalid site entry in menu file '" + menuFile.getAbsolutePath() + "'. Skipping item.");
					continue;
				}
				JSONObject siteEntry = (JSONObject) o;
				// Get site properties
				String link = (String) siteEntry.get("link");
				String nameDE = (String) siteEntry.get("name_de");
				String nameEN = (String) siteEntry.get("name_en");
				String group = (String) siteEntry.get("group");
				Number order = (Number) siteEntry.get("order");
				if (order == null) {
					order = 9999;
				}
				Boolean hidden = (Boolean) siteEntry.get("hidden");
				if (hidden == null) {
					hidden = false;
				}
				Map<String, String> additionalProperties = new HashMap<String, String>();
				// Remove pre-defined properties to find additional properties
				// that might be given
				siteEntry.remove("link");
				siteEntry.remove("name_de");
				siteEntry.remove("name_en");
				siteEntry.remove("hidden");
				siteEntry.remove("group");
				siteEntry.remove("order");
				for (Object k : siteEntry.keySet()) {
					additionalProperties.put((String) k, siteEntry.get(k).toString());
				}

				// Create menu item
				MenuItem newItem;
				try {
					newItem = new MenuItem(folder.getAbsolutePath().substring(rootFolderPathLen) + "/" + (link != null ? link : ""),
							nameDE, nameEN, group, order.intValue(), hidden, additionalProperties);
				} catch (IllegalArgumentException e) {
					System.out.println("Found invalid site entry in menu file '" + menuFile.getAbsolutePath() + "'. " + e.getMessage()
							+ " Skipping item.");
					continue;
				}

				// If a link is given, add the item as sub-element
				if (link == null) {
					siteMenuItem = newItem;
				} else {
					customSubItems.add(newItem);
				}
			}
		} finally {
			try {
				menuFileReader.close();
			} catch (IOException e) {
			}
		}

		if (siteMenuItem == null) {
			System.out.println("No main entry (empty link property) in menu file '" + menuFile.getAbsolutePath() + "'. Skipping path.");
			return;
		}

		siteMenuItem.childs.addAll(customSubItems);
		itemQueue.add(siteMenuItem);

		// Check subfolders
		for (File f : folder.listFiles()) {
			if (f.isDirectory()) {
				buildFolderItemQueue(f, siteMenuItem.getChildItemsQueue(), rootFolderPathLen);
			}
		}
	}

	/**
	 * Write ordered menu items in JSON format.
	 */
	private JSONArray buildMenuStructure(PriorityQueue<MenuItem> itemQueue) {

		JSONArray structureArray = new JSONArray();

		while (itemQueue.size() > 0) {
			MenuItem curItem = itemQueue.poll();

			JSONObject siteObject = new JSONObject();
			siteObject.put("name_de", curItem.getNameDE());
			siteObject.put("name_en", curItem.getNameEN());
			siteObject.put("link", curItem.getLink());
			siteObject.put("hidden", curItem.isHidden());

			siteObject.put("items", buildMenuStructure(curItem.getChildItemsQueue()));
			structureArray.add(siteObject);
		}
		return structureArray;
	}

	/**
	 * Website menu item
	 * 
	 */
	class MenuItem implements Comparable<MenuItem> {
		private final String link;
		private final String nameDE;
		private final String nameEN;
		private final String group;
		private final int order;
		private final boolean hidden;
		private final Map<String, String> additionalProperties;
		private final PriorityQueue<MenuItem> childs;

		public MenuItem(String link, String nameDE, String nameEN, String group, int order, boolean hidden,
				Map<String, String> additionalProperties) {
			if (nameDE == null && nameEN == null) {
				throw new IllegalArgumentException("Must provide name in at least one language.");
			}

			this.link = link;
			this.nameDE = nameDE != null ? nameDE : nameEN;
			this.nameEN = nameEN != null ? nameEN : nameDE;
			this.order = order;
			this.group = group;
			this.hidden = hidden;
			this.additionalProperties = additionalProperties;
			this.childs = new PriorityQueue<StructureBuilder.MenuItem>();

		}

		public String getLink() {
			return link;
		}

		public String getNameDE() {
			return nameDE;
		}

		public String getNameEN() {
			return nameEN;
		}

		public int getOrder() {
			return order;
		}

		public PriorityQueue<MenuItem> getChildItemsQueue() {
			return childs;
		}

		@Override
		public int compareTo(MenuItem o) {
			if (this.order != o.order) {
				return this.order - o.order;
			} else {
				return this.nameDE.compareTo(o.nameDE);
			}
		}

		public String getGroup() {
			return group;
		}

		public boolean isHidden() {
			return hidden;
		}

		public Map<String, String> getAdditionalProperties() {
			return additionalProperties;
		}
	}
}
