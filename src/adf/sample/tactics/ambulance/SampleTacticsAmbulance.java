package adf.sample.tactics.ambulance;

import adf.agent.action.Action;
import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.precompute.PrecomputeData;
import adf.component.algorithm.PathPlanning;
import adf.component.complex.TargetSelector;
import adf.component.tactics.TacticsAmbulance;
import adf.sample.algorithm.pathplanning.SamplePathPlanning;
import adf.sample.complex.targetselector.SearchBuildingSelector;
import adf.sample.complex.targetselector.VictimSelector;
import adf.sample.extaction.ActionSearchCivilian;
import adf.sample.extaction.ActionTransport;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class SampleTacticsAmbulance extends TacticsAmbulance {

    private PathPlanning pathPlanning;

    private TargetSelector<Human> victimSelector;
    private TargetSelector<Building> buildingSelector;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager) {
        worldInfo.indexClass(
                StandardEntityURN.CIVILIAN,
                StandardEntityURN.FIRE_BRIGADE,
                StandardEntityURN.POLICE_FORCE,
                StandardEntityURN.AMBULANCE_TEAM,
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.GAS_STATION,
                StandardEntityURN.AMBULANCE_CENTRE,
                StandardEntityURN.FIRE_STATION,
                StandardEntityURN.POLICE_OFFICE
        );
        this.pathPlanning = new SamplePathPlanning(agentInfo, worldInfo, scenarioInfo);
        this.victimSelector = new VictimSelector(agentInfo, worldInfo, scenarioInfo);
        this.buildingSelector = new SearchBuildingSelector(agentInfo, worldInfo, scenarioInfo, this.pathPlanning);
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, PrecomputeData precomputeData) {
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, PrecomputeData precomputeData) {
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo) {
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager) {
        this.pathPlanning.updateInfo();
        this.victimSelector.updateInfo();
        this.buildingSelector.updateInfo();

        Human injured = agentInfo.someoneOnBoard();
        if (injured != null) {
            return new ActionTransport(agentInfo, worldInfo, this.pathPlanning, injured).calc().getAction();
        }

        // Go through targets (sorted by distance) and check for things we can do
        EntityID target = this.victimSelector.calc().getTarget();
        if(target != null) {
            Action action = new ActionTransport(agentInfo, worldInfo, this.pathPlanning, (Human)worldInfo.getEntity(target)).calc().getAction();
            if(action != null) {
                return action;
            }
        }

        // Nothing to do
        return new ActionSearchCivilian(agentInfo, this.pathPlanning, this.buildingSelector).calc().getAction();
    }
}
