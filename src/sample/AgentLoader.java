package sample;

import adf.component.AbstractLoader;
import adf.component.control.ControlAmbulance;
import adf.component.control.ControlFire;
import adf.component.control.ControlPolice;
import adf.component.tactics.TacticsAmbulance;
import adf.component.tactics.TacticsFire;
import adf.component.tactics.TacticsPolice;
import sample.ambulance.tactics.MyTacticsAmbulance;
import sample.fire.tactics.MyTacticsFire;
import sample.police.tactics.MyTacticsPolice;

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
