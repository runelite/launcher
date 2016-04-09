package net.runelite.launcher.beans;

import org.eclipse.aether.artifact.DefaultArtifact;

public class Bootstrap
{
	private DefaultArtifact client;
	private String[] clientJvmArguments;

	public DefaultArtifact getClient()
	{
		return client;
	}

	public void setClient(DefaultArtifact client)
	{
		this.client = client;
	}

	public String[] getClientJvmArguments()
	{
		return clientJvmArguments;
	}

	public void setClientJvmArguments(String[] clientJvmArguments)
	{
		this.clientJvmArguments = clientJvmArguments;
	}
}
