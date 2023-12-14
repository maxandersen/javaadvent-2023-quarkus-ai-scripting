///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
//JAVAC_OPTIONS -parameters

//DEPS io.quarkus.platform:quarkus-bom:3.6.3@pom
//DEPS io.quarkiverse.langchain4j:quarkus-langchain4j-openai:0.4.0
//DEPS io.quarkus:quarkus-picocli

//Q:CONFIG quarkus.langchain4j.openai.timeout=60s
//  Q:CONFIG quarkus.langchain4j.openai.chat-model.model-name=gpt-4

//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=WARN
//Q:CONFIG quarkus.log.category."io.quarkiverse.langchain4j".level=WARN
//Q:CONFIG quarkus.log.category."dev.langchain4j".level=WARN
//Q:CONFIG quarkus.langchain4j.openai.log-requests=true
//Q:CONFIG quarkus.langchain4j.openai.log-responses=true

// To run in devmode use this command:
// `jbang --fresh -Dquarkus.dev -Dquarkus.console.enabled=false devhelper.java`
// This ensures fresh build, dev mode enabled and don't grab the console so
// you can ask questions in the terminal.

import static io.quarkus.logging.Log.info;
import static java.lang.System.out;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(mixinStandardHelpOptions = true, version = "0.1", header = "Have fun with Quarkus and LangChain4j", description = "Uses Quarkus LangChain4j to help understand and update your code.")
public class devhelper implements Runnable {

	@Parameters(description = "The question to answer", defaultValue = "What can you tell me about my project?")
	String question;

	@RegisterAiService(tools = FileManager.class)
	public interface ProjectHelper {

		@SystemMessage("""
				You are to help a developer understand his project. You can ask him questions or query the files in his project to get more info.
				If he asks you to modify a file, you can do so by calling the 'updateFile' tool.
				""")
		@UserMessage("{question}")
		String ask(String question);
	}

@Inject
ProjectHelper ai;

@ActivateRequestContext
public void run() {

	while (true) {
		var scanner = new Scanner(System.in);
		if (question == null || question.isBlank()) {
			out.println("Please enter your question:");
			question = scanner.nextLine();
			if (Set.of("exit", "quit").contains(question.toLowerCase())) {
				break;
			}
		}
		out.println("Thinking...");
		String answer = ai.ask(question);
		out.println("Answer: " + answer);
		question = null;
	}
}

	@ApplicationScoped
	static class FileManager {

		@Tool("""
				Get the files in a directory.
				The list of files recursively found in this directory.
				Will by default return all files in the root directory.
				""")
		public List<String> getFiles(
				@P("""
						The name of the directory relatively to the root of the project.
						Is a simple string. Use '/' to get the root directory.
						""") String directory)
				throws IOException {
			directory = handleDir(directory);
			info("Getting files in directory " + directory);

			var files = Files.list(Paths.get(directory)).map(p -> p.toString()).toList();

			return files;
		}

		@Tool("Get the content of a file")
		public String getFile(
				@P("""
						Read the content of a file.
						The parameter is a single string relative to the project root.
						""") String filename)
				throws IOException {
			filename = handleDir(filename);

			info("Getting content of file '" + filename + "'");

			var content = Files.readString(Paths.get(filename));
			return content;
		}

		@Tool("Create or update the content of a file")
		public void createOrUpdateFile(
				@P("Single string relative to the project root.") String filename,
				@P("The content of the file.") String content) throws IOException {

			filename = handleDir(filename);

			userConfirmation("Update file '" + filename + "' with content:" + content,
					"Are you sure you want to update the content of " + filename + "? (yes/no)");

			info("Updating content of file '" + filename + "'");

			Files.createDirectories(Paths.get(filename).toAbsolutePath().getParent());
			Files.writeString(Paths.get(filename), content);
		}

		@Tool("Remove file")
		public void removeFile(
				@P("Single string relative to the project root.") String filename) throws IOException {

			filename = handleDir(filename);

			userConfirmation("Delete file '" + filename,
					"Are you sure you want to delete " + filename + "? (yes/no)");

			info("Removing file '" + filename + "'");

			Files.delete(Paths.get(filename));
		}

// Check for '..' in the path to avoid accessing files outside of the project.
// Make request to / be the same as a request to . (root of current directory)
private String handleDir(String directory) {
	if (directory.contains("..")) {
		throw new IllegalArgumentException(
				"The path cannot contain '..' as it would allow to access files outside of the project.");
	}
	directory = (directory == null || directory.isBlank())? "/" : directory;
	return (directory.startsWith("/"))? directory.substring(1) : directory;
}

		private void userConfirmation(String message, String confirmationQuestion) throws IOException {
			Scanner scanner = new Scanner(System.in);
			out.println(message);
			out.println(confirmationQuestion);
			String response = scanner.nextLine();
			if (!Set.of("y", "yes").contains(response.toLowerCase())) {
				throw new IOException("Operation cancelled by the user.");
			}
		}
	}
}
