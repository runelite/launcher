package net.runelite.launcher;

import com.google.gson.Gson;
import java.util.List;
import net.runelite.launcher.beans.Bootstrap;
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
		resolver.addRepositories();
		Artifact a = new DefaultArtifact("net.runelite", "client", "", "jar", "1.0.0-SNAPSHOT");

		List<ArtifactResult> artifacts = resolver.resolveArtifacts(a);

		for (ArtifactResult a2 : artifacts)
			System.out.println(a2.getArtifact().getFile());
	}

	@Test
	public void printJson()
	{
		Bootstrap b = new Bootstrap();
		Gson g = new Gson();
		DefaultArtifact a = new DefaultArtifact("net.runelite", "client", "", "jar", "1.0.0-SNAPSHOT");
		b.setClient(a);
		b.setClientJvmArguments(new String[] {
			"-Xmx256m",
			"-Xss2m",
			"-Dsun.java2d.noddraw=true",
			"-XX:CompileThreshold=1500",
			"-Xincgc",
			"-XX:+UseConcMarkSweepGC",
			"-XX:+UseParNewGC"
		});
		System.out.println(g.toJson(b));
	}
}
