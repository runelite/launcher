package net.runelite.launcher;

import java.io.File;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactResult;

public class Launcher
{
	private static final File RUNELITE_DIR = new File(System.getProperty("user.home"), ".runelite");
	private static final File REPO_DIR = new File(RUNELITE_DIR, "repository");

	private static final String CLIENT_LAUNCHER_CLASS = "net.runelite.client.Launcher";

	public static void main(String[] args) throws Exception
	{
		ArtifactResolver resolver = new ArtifactResolver(REPO_DIR);
		Artifact a = new DefaultArtifact("net.runelite", "client", "", "jar", "1.0.0-SNAPSHOT"); // XXX

		List<ArtifactResult> results = resolver.resolveArtifacts(a);
		validate(resolver, results);
		RuneliteLoader loader = new RuneliteLoader(results);

		Launchable l = (Launchable) loader.loadClass(CLIENT_LAUNCHER_CLASS).newInstance();
		l.run();
	}

	private static void validate(ArtifactResolver resolver, List<ArtifactResult> artifacts)
	{
		for (ArtifactResult ar : artifacts)
		{
			Artifact a = ar.getArtifact();

			if (!a.getGroupId().startsWith("net.runelite"))
				continue;

			if (!ar.getRepository().equals(resolver.newRuneliteRepository()))
				throw new RuntimeException();
		}
	}
}
