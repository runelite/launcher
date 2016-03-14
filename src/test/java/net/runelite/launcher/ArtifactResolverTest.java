package net.runelite.launcher;

import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.util.artifact.DefaultArtifact;

public class ArtifactResolverTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void test() throws Exception
	{
		ArtifactResolver resolver = new ArtifactResolver(folder.newFolder(), new RemoteRepository("runelite", null, "http://192.168.1.2/rs/repo"));
		Artifact a = new DefaultArtifact("net.runelite.rs", "client", "", "jar", "1.0.0-SNAPSHOT");

		List<Artifact> artifacts = resolver.resolveArtifacts(a);

		for (Artifact a2 : artifacts)
			System.out.println(a2);
	}

}
