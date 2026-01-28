package com.jeffdisher.october.peaks.animation;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.badlogic.gdx.files.FileHandle;
import com.jeffdisher.october.types.EntityLocation;


public class TestRigging
{
	@ClassRule
	public static TemporaryFolder DIRECTORY = new TemporaryFolder();

	@Test
	public void basicLoad() throws Throwable
	{
		// Just show the basics of what we get from this helper.
		File file = DIRECTORY.newFile();
		String tablist = "BODY	BODY\n"
			+ "	base	0.0	0.0	0.0\n"
			+ "HEAD	PITCH\n"
			+ "	base	0.0	0.0	1.0\n"
			+ "FR	POSITIVE\n"
			+ "	base	0.0	1.0	0.0\n"
			+ "FL	NEGATIVE\n"
			+ "	base	1.0	0.0	0.0\n"
			+ "\n"
		;
		Files.writeString(file.toPath(), tablist);
		
		List<Rigging.LimbRig> limbs = Rigging.loadFromTablistFile(new FileHandle(file));
		Assert.assertEquals(4, limbs.size());
		
		Rigging.LimbRig body = limbs.get(0);
		Assert.assertEquals("BODY", body.name());
		Assert.assertEquals(Rigging.ComponentType.BODY, body.type());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), body.base());
		
		Rigging.LimbRig head = limbs.get(1);
		Assert.assertEquals("HEAD", head.name());
		Assert.assertEquals(Rigging.ComponentType.PITCH, head.type());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 1.0f), head.base());
		
		Rigging.LimbRig frontRight = limbs.get(2);
		Assert.assertEquals("FR", frontRight.name());
		Assert.assertEquals(Rigging.ComponentType.POSITIVE, frontRight.type());
		Assert.assertEquals(new EntityLocation(0.0f, 1.0f, 0.0f), frontRight.base());
		
		Rigging.LimbRig frontLeft = limbs.get(3);
		Assert.assertEquals("FL", frontLeft.name());
		Assert.assertEquals(Rigging.ComponentType.NEGATIVE, frontLeft.type());
		Assert.assertEquals(new EntityLocation(1.0f, 0.0f, 0.0f), frontLeft.base());
	}
}
