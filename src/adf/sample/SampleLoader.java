package adf.sample;

import adf.component.AbstractLoader;
import adf.component.tactics.TacticsAmbulance;
import adf.component.tactics.TacticsFire;
import adf.component.tactics.TacticsPolice;
import adf.component.tactics.center.TacticsAmbulanceCenter;
import adf.component.tactics.center.TacticsFireCenter;
import adf.component.tactics.center.TacticsPoliceCenter;
import adf.sample.tactics.center.SampleAmbulanceCenter;
import adf.sample.tactics.center.SampleFireCenter;
import adf.sample.tactics.center.SamplePoliceCenter;
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
    public TacticsAmbulanceCenter getTacticsAmbulanceCenter() {
        return new SampleAmbulanceCenter();
    }

    @Override
    public TacticsFireCenter getTacticsFireCenter() {
        return new SampleFireCenter();
    }

    @Override
    public TacticsPoliceCenter getTacticsPoliceCenter() {
        return new SamplePoliceCenter();
    }
}
