package adf.sample;

import adf.component.AbstractLoader;
import adf.component.control.ControlAmbulance;
import adf.component.control.ControlFire;
import adf.component.control.ControlPolice;
import adf.component.tactics.TacticsAmbulance;
import adf.component.tactics.TacticsFire;
import adf.component.tactics.TacticsPolice;
import adf.sample.tactics.ambulance.SampleTacticsAmbulance;
import adf.sample.tactics.fire.SampleTacticsFire;
import adf.sample.tactics.police.SampleTacticsPolice;

public class SampleLoader extends AbstractLoader {
    @Override
    public String getTeamName() {
        return "Sample";
    }

    @Override
    public TacticsAmbulance getTacticsAmbulance() {
        return new SampleTacticsAmbulance();
    }

    @Override
    public TacticsFire getTacticsFire() {
        return new SampleTacticsFire();
    }

    @Override
    public TacticsPolice getTacticsPolice() {
        return new SampleTacticsPolice();
    }

    @Override
    public ControlAmbulance getControlAmbulance() {
        return null;
    }

    @Override
    public ControlFire getControlFire() {
        return null;
    }

    @Override
    public ControlPolice getControlPolice() {
        return null;
    }
}
