package net.runelite.launcher;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import org.eclipse.aether.resolution.ArtifactResult;

final class RuneliteLoader extends URLClassLoader
{
	RuneliteLoader(List<ArtifactResult> artifacts) throws MalformedURLException
	{
		super(new URL[0]);

		for (ArtifactResult ar : artifacts)
		{
			File f = ar.getArtifact().getFile();

			this.addURL(f.toURI().toURL());
		}
	}
}
