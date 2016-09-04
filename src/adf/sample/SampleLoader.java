package adf.sample;

import adf.component.AbstractLoader;
import adf.component.control.ControlAmbulance;
import adf.component.control.ControlFire;
import adf.component.control.ControlPolice;
import adf.component.tactics.TacticsAmbulance;
import adf.component.tactics.TacticsFire;
import adf.component.tactics.TacticsPolice;
import adf.sample.control.SampleControlAmbulance;
import adf.sample.control.SampleControlFire;
import adf.sample.control.SampleControlPolice;
import adf.sample.tactics.SampleAmbulance;
import adf.sample.tactics.SampleFire;
import adf.sample.tactics.SamplePolice;

public class SampleLoader extends AbstractLoader {
    @Override
    public String getTeamName() {
        return "Sample";
    }

    @Override
    public TacticsAmbulance getTacticsAmbulance() {
        return new SampleAmbulance();
    }

    @Override
    public TacticsFire getTacticsFire() {
        return new SampleFire();
    }

    @Override
    public TacticsPolice getTacticsPolice() {
        return new SamplePolice();
    }

    @Override
    public ControlAmbulance getControlAmbulance() {
        return new SampleControlAmbulance();
    }

    @Override
    public ControlFire getControlFire() {
        return new SampleControlFire();
    }

    @Override
    public ControlPolice getControlPolice() {
        return new SampleControlPolice();
    }
}
