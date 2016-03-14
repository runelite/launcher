package net.runelite.launcher;

import com.jcabi.aether.Aether;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.DependencyResolutionException;

public class ArtifactResolver
{
	private final File repositoryCache;
	private final RemoteRepository remoteRepository;

	public ArtifactResolver(File repositoryCache, RemoteRepository remoteRepository)
	{
		this.repositoryCache = repositoryCache;
		this.remoteRepository = remoteRepository;
	}

	public List<Artifact> resolveArtifacts(Artifact artifact) throws DependencyResolutionException
	{
		Aether a = new Aether(Arrays.asList(remoteRepository), repositoryCache);

		Collection<Artifact> deps = a.resolve(artifact, "runtime");
		return new ArrayList<>(deps);
	}
}
