package adf.sample;

import adf.component.AbstractLoader;
import adf.component.tactics.TacticsAmbulanceTeam;
import adf.component.tactics.TacticsFireBrigade;
import adf.component.tactics.TacticsPoliceForce;
import adf.component.tactics.TacticsAmbulanceCentre;
import adf.component.tactics.TacticsFireStation;
import adf.component.tactics.TacticsPoliceOffice;
import adf.sample.tactics.SampleTacticsAmbulanceCentre;
import adf.sample.tactics.SampleTacticsFireStation;
import adf.sample.tactics.SampleTacticsPoliceOffice;
import adf.sample.tactics.SampleTacticsAmbulanceTeam;
import adf.sample.tactics.SampleTacticsFireBrigade;
import adf.sample.tactics.SampleTacticsPoliceForce;

public class SampleLoader extends AbstractLoader {
    @Override
    public String getTeamName() {
        return "Sample";
    }

    @Override
    public TacticsAmbulanceTeam getTacticsAmbulanceTeam() {
        return new SampleTacticsAmbulanceTeam();
    }

    @Override
    public TacticsFireBrigade getTacticsFireBrigade() {
        return new SampleTacticsFireBrigade();
    }

    @Override
    public TacticsPoliceForce getTacticsPoliceForce() {
        return new SampleTacticsPoliceForce();
    }

    @Override
    public TacticsAmbulanceCentre getTacticsAmbulanceCentre() {
        return new SampleTacticsAmbulanceCentre();
    }

    @Override
    public TacticsFireStation getTacticsFireStation() {
        return new SampleTacticsFireStation();
    }

    @Override
    public TacticsPoliceOffice getTacticsPoliceOffice() {
        return new SampleTacticsPoliceOffice();
    }
}
