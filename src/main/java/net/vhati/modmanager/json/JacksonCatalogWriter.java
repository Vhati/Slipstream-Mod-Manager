package net.vhati.modmanager.json;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.vhati.modmanager.core.ModsInfo;

public class JacksonCatalogWriter {

	/**
	 * Writes collated catalog entries to a file, as condensed json.
	 */
	public static void write(List<ModsInfo> modsInfoList, File dstFile) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode rootNode = mapper.createObjectNode();

		ObjectNode catalogsNode = rootNode.objectNode();
		rootNode.set("catalog_versions", catalogsNode);

		ArrayNode catalogNode = rootNode.arrayNode();
		catalogsNode.set("1", catalogNode);

		for (ModsInfo modsInfo : modsInfoList) {
			ObjectNode infoNode = rootNode.objectNode();
			catalogNode.add(infoNode);

			infoNode.put("title", modsInfo.getTitle());
			infoNode.put("author", modsInfo.getAuthor());
			infoNode.put("desc", modsInfo.getDescription());
			infoNode.put("url", modsInfo.getThreadURL());

			infoNode.put("thread_hash", modsInfo.threadHash);

			ArrayNode versionsNode = rootNode.arrayNode();
			infoNode.set("versions", versionsNode);

			for (Map.Entry<String, String> entry : modsInfo.getVersionsMap().entrySet()) {
				String versionFileHash = entry.getKey();
				String versionString = entry.getValue();

				ObjectNode versionNode = rootNode.objectNode();
				versionNode.put("hash", versionFileHash);
				versionNode.put("version", versionString);
				versionsNode.add(versionNode);
			}
		}

		OutputStream os = null;
		try {
			os = new FileOutputStream(dstFile);
			OutputStreamWriter writer = new OutputStreamWriter(os, Charset.forName("US-ASCII"));
			mapper.writeValue(writer, rootNode);
		} finally {
			try {
				if (os != null)
					os.close();
			} catch (IOException e) {
			}
		}
	}
}
