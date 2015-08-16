package net.runelite.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Util
{
	private static final Logger logger = LoggerFactory.getLogger(Util.class);
	
	public static File getRuneliteDir()
	{
		File userHome = new File(System.getProperty("user.home"));
		
		File rldir = new File(userHome, ".runelite");
		rldir.mkdir();
		
		return rldir;
	}
	
	private static boolean isCached(URL source, File dest) throws IOException
	{
		if (!dest.exists())
			return false;
		
		HttpURLConnection connection = (HttpURLConnection) source.openConnection();
		connection.setRequestMethod("HEAD");
		
		if (connection.getResponseCode() != 200)
		{
			logger.error("Bad response from server {}", 200);
			return false;
		}
		
		String lastModified = connection.getHeaderField("Last-Modified");
		Date lastModifiedDate;
		try
		{
			lastModifiedDate = DateUtils.parseDate(lastModified);
		}
		catch (DateParseException ex)
		{
			logger.error("Unable to parse last modified header", ex);
			return false;
		}
		
		Date lastModifiedFile = new Date(dest.lastModified());
		
		logger.info("File {} ({}) is cached at {} ({})", source, lastModifiedDate, dest, lastModifiedFile);
		return lastModifiedFile.after(lastModifiedDate);
	}
	
	public static void download(URL source, File dest) throws IOException
	{
		if (isCached(source, dest))
			return;
		
		logger.info("Downloading {} to {}", source, dest);
		
		HttpURLConnection connection = (HttpURLConnection) source.openConnection();
		byte[] buf = new byte[8192];
		
		try (InputStream in = connection.getInputStream())
		{
			try (FileOutputStream fout = new FileOutputStream(dest))
			{
				for (int i; (i = in.read(buf)) != -1;)
				{
					fout.write(buf, 0, i);
				}
			}
		}
		
		logger.info("Download finished");
	}
}
