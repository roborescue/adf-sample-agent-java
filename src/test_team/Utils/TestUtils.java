package test_team.Utils;

import static rescuecore2.standard.entities.StandardEntityURN.AMBULANCE_TEAM;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

import adf.agent.info.WorldInfo;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;

public class TestUtils {

	public static boolean isValidHuman(WorldInfo worldInfo,StandardEntity entity) {
		if (entity == null)
			return false;
		if (!(entity instanceof Human))
			return false;

		Human target = (Human) entity;
		if (!target.isHPDefined() || target.getHP() == 0)
			return false;
		if (!target.isPositionDefined())
			return false;
		if (!target.isDamageDefined() || target.getDamage() == 0)
			return false;
		if (!target.isBuriednessDefined())
			return false;

		StandardEntity position = worldInfo.getPosition(target);
		if (position == null)
			return false;

		StandardEntityURN positionURN = position.getStandardURN();
		if (positionURN == REFUGE || positionURN == AMBULANCE_TEAM)
			return false;

		return true;
	}
}
