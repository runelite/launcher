package net.runelite.launcher;

import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ArtifactResolverTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void test() throws Exception
	{
		ArtifactResolver resolver = new ArtifactResolver(folder.newFolder());
		Artifact a = new DefaultArtifact("net.runelite", "client", "", "jar", "1.0.0-SNAPSHOT");

		List<ArtifactResult> artifacts = resolver.resolveArtifacts(a);

		for (ArtifactResult a2 : artifacts)
			System.out.println(a2.getArtifact().getFile());
	}
}
