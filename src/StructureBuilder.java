import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class StructureBuilder {
	private String folderPath;
	private String outputFilePath;

	private final Pattern templateReplacementPattern = Pattern.compile("\\{\\{(.*?)\\}\\}");

	/**
	 * Constructor.
	 */
	public StructureBuilder(String folderPath, String outputFilePath) {
		this.folderPath = folderPath;
		this.outputFilePath = outputFilePath;
	}

	/**
	 * Writes the site structure file and builds the group files from the
	 * provided templates.
	 */
	public void build() {
		TreeSet<MenuItem> itemQueue = new TreeSet<StructureBuilder.MenuItem>();
		List<AbstractMap.SimpleImmutableEntry<String, MenuItem>> groupTemplateFiles = new ArrayList<AbstractMap.SimpleImmutableEntry<String, MenuItem>>();

		System.out.println("Parsing menu files ...");
		File folder = new File(folderPath);
		if (!folder.exists()) {
			System.err.println("Could not find path '" + folder.getAbsolutePath() + "'. Abort.");
			return;
		}
		int rootFolderPathLen = folder.getAbsolutePath().length();
		buildFolderItemQueue(folder, itemQueue, rootFolderPathLen, groupTemplateFiles);

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
			System.err.println("Could not write output file '" + outputFilePath + '.');
			return;
		} catch (IOException e) {
			System.err.println("Could not write output file '" + outputFilePath + '.');
			return;
		}

		System.out.println("Writing group files from templates ...");
		for (AbstractMap.SimpleImmutableEntry<String, MenuItem> groupTemplate : groupTemplateFiles) {
			System.out.println("   " + groupTemplate.getKey().substring(folder.getAbsolutePath().length()) + " ...");
			writeGroupFileFromTemplate(groupTemplate.getKey(), groupTemplate.getValue());
		}

		System.out.println("Done.");
	}

	/**
	 * Builds a prioritized list of menu items and a list of group template
	 * files.
	 */
	private void buildFolderItemQueue(File folder, TreeSet<MenuItem> itemQueue, int rootFolderPathLen,
			List<AbstractMap.SimpleImmutableEntry<String, MenuItem>> groupTemplateFiles) {

		MenuItem siteMenuItem = null;
		Set<MenuItem> customSubItems = new HashSet<MenuItem>();

		// Read and parse menu file
		File menuFile = new File(folder.getAbsolutePath() + "/.menu");
		InputStreamReader menuFileReader;
		try {
			menuFileReader = new InputStreamReader(new FileInputStream(menuFile));
		} catch (FileNotFoundException e) {
			System.err.println("Could not find menu file '" + folder.getAbsolutePath() + "'. Skipping path.");
			return;
		}
		try {
			JSONArray menuItemArray;
			try {
				JSONParser parser = new JSONParser();
				Object parsedMenuFile = parser.parse(menuFileReader);
				if (!(parsedMenuFile instanceof JSONArray)) {
					System.err.println("Menu file " + menuFile.getAbsolutePath() + " does not contain a JSON array. Skipping path.");
					return;
				}
				menuItemArray = (JSONArray) parsedMenuFile;
			} catch (IOException e1) {
				System.err.println("Could not read menu file '" + folder.getAbsolutePath() + "'. Skipping path.");
				return;
			} catch (ParseException e) {
				System.err.println("Menu file '" + menuFile.getAbsolutePath() + "' does not contain a valid JSON array. " + e.toString()
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
				String fullLink = folder.getAbsolutePath().substring(rootFolderPathLen) + "/" + (link != null ? link : "");
				String nameDE = (String) siteEntry.get("name_de");
				String nameEN = (String) siteEntry.get("name_en");
				String group = (String) siteEntry.get("group");
				Object orderObj = siteEntry.get("order");
				Number order;
				if (orderObj != null) {
					if (orderObj instanceof Number) {
						order = (Number) siteEntry.get("order");
					} else {
						order = simpleOrderPreservingHash(siteEntry.get("order").toString());
					}
				} else {
					order = 9999;
				}
				Boolean hidden = (Boolean) siteEntry.get("hidden");
				if (hidden == null) {
					hidden = false;
				}
				Map<String, String> allProperties = new HashMap<String, String>();
				for (Object k : siteEntry.keySet()) {
					allProperties.put((String) k, siteEntry.get(k).toString());
				}

				// Create menu item
				MenuItem newItem;
				try {
					newItem = new MenuItem(fullLink, nameDE, nameEN, group, order.intValue(), hidden, allProperties);
				} catch (IllegalArgumentException e) {
					System.err.println("Found invalid site entry in menu file '" + menuFile.getAbsolutePath() + "'. " + e.getMessage()
							+ " Skipping item.");
					continue;
				}

				// Update sanitized properties
				allProperties.put("link", newItem.getLink());
				allProperties.put("hidden", ((Boolean) newItem.isHidden()).toString());
				allProperties.put("name_de", newItem.getNameDE());
				allProperties.put("name_en", newItem.getNameEN());

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
			System.err.println("No main entry (empty link property) in menu file '" + menuFile.getAbsolutePath() + "'. Skipping path.");
			return;
		}

		siteMenuItem.childs.addAll(customSubItems);
		itemQueue.add(siteMenuItem);

		// Check subfolders and find template files
		for (File f : folder.listFiles()) {
			if (f.isDirectory()) {
				buildFolderItemQueue(f, siteMenuItem.getChildItemsQueue(), rootFolderPathLen, groupTemplateFiles);
			}

			if (f.getName().startsWith(".template.")) {
				groupTemplateFiles.add(new SimpleImmutableEntry<String, StructureBuilder.MenuItem>(f.getAbsolutePath(), siteMenuItem));
			}
		}
	}

	/**
	 * Generates an 32-bit integer hash value which is order-preserving. Note
	 * that this method is very simple and does not work well for non-letters or
	 * letters outside the ANSI charset.
	 */
	private int simpleOrderPreservingHash(String str) {
		int hash = 10000; // Min hash is 10000
		final int maxHashLen = 5;
		int hashLen = Math.min(str.length(), maxHashLen);
		final int possibleHasedCharacters = 43;
		String strLower = str.toLowerCase();
		for (int c = 0; c < hashLen; c++) {
			// Encode character
			int charCode;
			char curChar = strLower.charAt(c);
			if (curChar == ' ') {
				charCode = 0;
			} else if (curChar >= 48 && curChar <= 57) {
				// Next 10 codes are numbers
				charCode = curChar - 47;
			} else if (curChar >= 97 && curChar <= 122) {
				// Next 26 codes are letters
				charCode = curChar - 86;
			} else if (curChar == 'ä') {
				// Special char for German language
				charCode = 38;
			} else if (curChar == 'ö') {
				// Special char for German language
				charCode = 39;
			} else if (curChar == 'ü') {
				// Special char for German language
				charCode = 40;
			} else if (curChar == 'ß') {
				// Special char for German language
				charCode = 41;
			} else {
				charCode = 42;
			}
			// Add to hash encoded as base possibleHasedCharacters
			hash += charCode * Math.pow(possibleHasedCharacters, maxHashLen - 1 - c);
		}
		return hash;
	}

	/**
	 * Write ordered menu items in JSON format.
	 */
	private JSONArray buildMenuStructure(TreeSet<MenuItem> itemQueue) {

		JSONArray structureArray = new JSONArray();

		for (MenuItem curItem : itemQueue) {
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
	 * Writes a group file for a given template.
	 */
	private void writeGroupFileFromTemplate(String templatePath, MenuItem belongingMenuItem) {
		// Read template
		File templateFile = new File(templatePath);

		String itemTemplate;
		String itemSpacer;

		InputStreamReader templateFileReader;
		try {
			templateFileReader = new InputStreamReader(new FileInputStream(templateFile));
		} catch (FileNotFoundException e) {
			System.err.println("Template '" + templatePath + "' could not be opened. Skipping template.");
			return;
		}
		try {
			JSONParser parser = new JSONParser();
			Object parsedTemplateFile;
			try {
				parsedTemplateFile = parser.parse(templateFileReader);
			} catch (IOException e) {
				System.err.println("Template '" + templatePath + "' could not be read. Skipping template.");
				return;
			} catch (ParseException e) {
				System.err.println("Template '" + templatePath + "' is not a valid JSON file. " + e.toString() + " Skipping template.");
				return;
			}
			if (!(parsedTemplateFile instanceof JSONObject)) {
				System.err.println("Template '" + templatePath + "' is not a valid JSON file. Skipping template.");
				return;
			}

			JSONObject templateObject = (JSONObject) parsedTemplateFile;
			if (!templateObject.containsKey("item_template")) {
				System.err.println("Template '" + templatePath + "' does not contain a 'item_template' entry. Skipping template.");
				return;
			}
			itemTemplate = (String) templateObject.get("item_template");
			itemSpacer = (String) templateObject.get("item_spacer");
		} finally {
			try {
				templateFileReader.close();
			} catch (IOException e) {
			}
		}

		// Find group items
		String groupName = templateFile.getName().substring(10 /* .template. */);
		Map<String, TreeSet<MenuItem>> groupItems = new HashMap<String, TreeSet<MenuItem>>();

		findGroupItems(belongingMenuItem.childs, groupName, groupItems);

		// Write templates
		for (Map.Entry<String, TreeSet<MenuItem>> curGroup : groupItems.entrySet()) {
			String groupFileOutPath = templateFile.getAbsolutePath().substring(0,
					templateFile.getAbsolutePath().length() - templateFile.getName().length())
					+ "." + curGroup.getKey() + ".html";

			BufferedWriter outputWriter;
			try {
				outputWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(groupFileOutPath)));
			} catch (FileNotFoundException e) {
				System.err.println("Could not write group file '" + groupFileOutPath + "'. Skipping template '" + templatePath + "'.");
				return;
			}

			try {
				try {
					boolean firstItem = true;
					for (MenuItem groupItem : curGroup.getValue()) {
						if (!firstItem) {
							outputWriter.write(itemSpacer);
							firstItem = false;
						}

						// Build and write item template
						String curItemTemplate = "";
						Matcher itemTemplateReplacementMatcher = templateReplacementPattern.matcher(itemTemplate);
						int lastMatchPos = 0;
						while (itemTemplateReplacementMatcher.find()) {
							curItemTemplate += itemTemplate.substring(lastMatchPos, itemTemplateReplacementMatcher.start());

							String placeholderName = itemTemplateReplacementMatcher.group(1);
							if (groupItem.getAllProperties().containsKey(placeholderName)) {
								curItemTemplate += groupItem.getAllProperties().get(placeholderName);
							}

							lastMatchPos = itemTemplateReplacementMatcher.end();
						}
						curItemTemplate += itemTemplate.substring(lastMatchPos);

						outputWriter.write(curItemTemplate);
					}
				} finally {
					outputWriter.close();
				}
			} catch (IOException e) {
				System.err.println("Could not write group file '" + groupFileOutPath + "'. Skipping template '" + templatePath + "'.");
				return;
			}
		}
	}

	/**
	 * Finds all menu items belonging to a group or a sub-group.
	 */
	private void findGroupItems(TreeSet<MenuItem> items, String groupName, Map<String, TreeSet<MenuItem>> groupItems) {
		final String lowerGroupName = groupName.toLowerCase();
		for (MenuItem i : items) {
			if (i.getGroup() != null) {
				if (i.getGroup().toLowerCase().startsWith(lowerGroupName)) {
					if (groupItems.get(i.getGroup()) == null) {
						groupItems.put(i.getGroup(), new TreeSet<MenuItem>());
					}
					groupItems.get(i.getGroup()).add(i);
				}
			}
			findGroupItems(i.childs, groupName, groupItems);
		}
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
		private final Map<String, String> allProperties;
		private final TreeSet<MenuItem> childs;

		public MenuItem(String link, String nameDE, String nameEN, String group, int order, boolean hidden,
				Map<String, String> allProperties) {
			if (nameDE == null && nameEN == null) {
				throw new IllegalArgumentException("Must provide name in at least one language.");
			}

			this.link = link;
			this.nameDE = nameDE != null ? nameDE : nameEN;
			this.nameEN = nameEN != null ? nameEN : nameDE;
			this.order = order;
			this.group = group;
			this.hidden = hidden;
			this.allProperties = allProperties;
			this.childs = new TreeSet<StructureBuilder.MenuItem>();

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

		public TreeSet<MenuItem> getChildItemsQueue() {
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

		public Map<String, String> getAllProperties() {
			return allProperties;
		}
	}
}
