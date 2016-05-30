package adf.challenge.tactics;

import adf.agent.action.Action;
import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.HumanSelector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsAmbulance;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class ChallengeAmbulance extends TacticsAmbulance {
    private PathPlanning pathPlanning;

    private HumanSelector victimSelector;
    private Search search;

    private Clustering clustering;
    private int clusterIndex;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
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
        this.clusterIndex = -1;
        //new SamplePathPlanning(agentInfo, worldInfo, scenarioInfo, this.moduleManager);
        this.pathPlanning = moduleManager.getModule("adf.component.module.algorithm.PathPlanning");
        //init ExtAction
        moduleManager.getExtAction("ActionTransport");
        moduleManager.getExtAction("ActionSearchCivilian");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule("adf.sample.module.algorithm.clustering.PathBasedKMeans");
        this.clustering.calc();
        this.clustering.precompute(precomputeData);
        this.victimSelector = moduleManager.getModule("adf.sample.module.complex.clustering.ClusteringVictimSelector");
        this.victimSelector.precompute(precomputeData);
        this.search = moduleManager.getModule("adf.sample.module.complex.clustering.ClusteringSearchBuilding");
        this.search.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule("adf.sample.module.algorithm.clustering.PathBasedKMeans");
        this.clustering.resume(precomputeData);
        this.victimSelector = moduleManager.getModule("adf.sample.module.complex.clustering.ClusteringVictimSelector");
        this.victimSelector.resume(precomputeData);
        this.search = moduleManager.getModule("adf.sample.module.complex.clustering.ClusteringSearchBuilding");
        this.search.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        this.clustering = moduleManager.getModule("adf.sample.module.algorithm.clustering.StandardKMeans");
        this.clustering.calc();
        this.victimSelector = moduleManager.getModule("adf.sample.module.complex.clustering.ClusteringVictimSelector");
        this.search = moduleManager.getModule("adf.sample.module.complex.clustering.ClusteringSearchBuilding");
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        this.pathPlanning.updateInfo(messageManager);
        this.victimSelector.updateInfo(messageManager);
        this.search.updateInfo(messageManager);

        Human injured = agentInfo.someoneOnBoard();
        if (injured != null) {
            return moduleManager.getExtAction("ActionTransport").setTarget(injured.getID()).calc().getAction();
        }

        // Go through targets (sorted by distance) and check for things we can do
        EntityID target = this.victimSelector.calc().getTarget();
        if(target != null) {
            Action action = moduleManager.getExtAction("ActionTransport").setTarget(target).calc().getAction();
            if(action != null) {
                return action;
            }
        }

        // Nothing to do
        return moduleManager.getExtAction("ActionSearchCivilian").calc().getAction();
    }
}
