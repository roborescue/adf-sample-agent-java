package adf.sample;

import adf.control.ControlAmbulance;
import adf.control.ControlFire;
import adf.control.ControlPolice;
import adf.launcher.AbstractLoader;
import adf.sample.ambulance.tactics.MyTacticsAmbulance;
import adf.sample.fire.tactics.MyTacticsFire;
import adf.sample.police.tactics.MyTacticsPolice;
import adf.tactics.TacticsAmbulance;
import adf.tactics.TacticsFire;
import adf.tactics.TacticsPolice;

/**
 * Created by takamin on 10/14/15.
 */
public class AgentLoader extends AbstractLoader
{
	@Override
	public String getTeamName()
	{
		return "MyTeam";
	}

	@Override
	public TacticsAmbulance getTacticsAmbulance()
	{
		return new MyTacticsAmbulance();
	}

	@Override
	public TacticsFire getTacticsFire()
	{
		return new MyTacticsFire();
	}

	@Override
	public TacticsPolice getTacticsPolice()
	{
		return new MyTacticsPolice();
	}

	@Override
	public ControlAmbulance getControlAmbulance()
	{
		return null;
	}

	@Override
	public ControlFire getControlFire()
	{
		return null;
	}

	@Override
	public ControlPolice getControlPolice()
	{
		return null;
	}
}
