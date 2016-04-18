package net.runelite.launcher;

import com.google.gson.Gson;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.launcher.beans.Bootstrap;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResult;

public class Launcher
{
	private static final File RUNELITE_DIR = new File(System.getProperty("user.home"), ".runelite");
	private static final File REPO_DIR = new File(RUNELITE_DIR, "repository");

	private static final String CLIENT_BOOTSTRAP_URL = "https://static.runelite.net/bootstrap.json";
	private static final String CLIENT_MAIN_CLASS = "net.runelite.client.RuneLite";

	private static String getJava()
	{
		String path = System.getProperty("java.library.path");
		int i = path.indexOf(File.pathSeparator);
		if (i == -1)
			return path;

		path = path.substring(0, i);

		File java = new File(path, "java.exe");
		if (java.exists())
			return java.getAbsolutePath();
		else
			return new File(path, "java").getAbsolutePath();
	}

	public static void main(String[] args) throws Exception
	{
		Bootstrap bootstrap = getBootstrap();

		ArtifactResolver resolver = new ArtifactResolver(REPO_DIR);
		Artifact a = bootstrap.getClient();

		List<ArtifactResult> results = resolver.resolveArtifacts(a);
		StringBuilder classPath = new StringBuilder();

		for (ArtifactResult ar : results)
		{
			File f = ar.getArtifact().getFile();

			if (classPath.length() > 0)
				classPath.append(File.pathSeparatorChar);

			classPath.append(f.getAbsolutePath());
		}

		List<String> arguments = new ArrayList<>();
		arguments.add(getJava());
		arguments.add("-cp"); arguments.add(classPath.toString());
		arguments.addAll(Arrays.asList(bootstrap.getClientJvmArguments()));
		arguments.add(CLIENT_MAIN_CLASS);

		Runtime.getRuntime().exec(arguments.toArray(new String[0]));
	}

	private static Bootstrap getBootstrap() throws Exception
	{
		URL u = new URL(CLIENT_BOOTSTRAP_URL);
		URLConnection conn = u.openConnection();
		conn.setRequestProperty("User-Agent", "Mozilla/5.0");
		try (InputStream i = conn.getInputStream())
		{
			Gson g = new Gson();
			return g.fromJson(new InputStreamReader(i), Bootstrap.class);
		}
	}
}
