package net.runelite.launcher;

import com.google.gson.Gson;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactResult;

public class Launcher
{
	private static final File RUNELITE_DIR = new File(System.getProperty("user.home"), ".runelite");
	public static final File REPO_DIR = new File(RUNELITE_DIR, "repository");

	private static final String CLIENT_ARTIFACT_URL = "https://static.runelite.net/bootstrap.json";
	private static final String CLIENT_LAUNCHER_CLASS = "net.runelite.client.Launcher";

	public static void main(String[] args) throws Exception
	{
		ArtifactResolver resolver = new ArtifactResolver(REPO_DIR);
		Artifact a = getClientArtifact();

		List<ArtifactResult> results = resolver.resolveArtifacts(a);
		RuneliteLoader loader = new RuneliteLoader(results);

		Launchable l = (Launchable) loader.loadClass(CLIENT_LAUNCHER_CLASS).newInstance();
		l.run();
	}

	private static Artifact getClientArtifact() throws Exception
	{
		URL u = new URL(CLIENT_ARTIFACT_URL);
		URLConnection conn = u.openConnection();
		conn.setRequestProperty("User-Agent", "Mozilla/5.0");
		try (InputStream i = conn.getInputStream())
		{
			Gson g = new Gson();
			return g.fromJson(new InputStreamReader(i), DefaultArtifact.class);
		}
	}
}
