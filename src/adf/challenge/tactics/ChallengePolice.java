package adf.challenge.tactics;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.challenge.SampleModuleKey;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.BlockadeSelector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsPolice;
import adf.util.WorldUtil;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;

public class ChallengePolice extends TacticsPolice {

    private PathPlanning pathPlanning;

    private BlockadeSelector blockadeSelector;
    private Search search;

    private Clustering clustering;
    private int clusterIndex;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.BLOCKADE
        );
        this.clusterIndex = -1;
        //init ExtAction
        moduleManager.getExtAction(SampleModuleKey.POLICE_ACTION_EXT_CLEAR);
        moduleManager.getExtAction(SampleModuleKey.POLICE_ACTION_SEARCH);
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING);
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_CLUSTERING);
        this.clustering.precompute(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_SEARCH);
        this.search.precompute(precomputeData);
        this.blockadeSelector = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_BLOCKADE_SELECTOR);
        this.blockadeSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING);
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_CLUSTERING);
        this.clustering.resume(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_SEARCH);
        this.search.resume(precomputeData);
        this.blockadeSelector = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_BLOCKADE_SELECTOR);
        this.blockadeSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING);
        this.pathPlanning.preparate();
        this.clustering = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_CLUSTERING);
        this.clustering.preparate();
        this.search = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_SEARCH);
        this.search.preparate();
        this.blockadeSelector = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_BLOCKADE_SELECTOR);
        this.blockadeSelector.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.blockadeSelector.updateInfo(messageManager);
        this.search.updateInfo(messageManager);

        if(this.clusterIndex == -1) {
            this.clusterIndex = this.clustering.getClusterIndex(agentInfo.getID());
        }
        Collection<StandardEntity> list = this.clustering.getClusterEntities(this.clusterIndex);
        if(!list.contains(agentInfo.me())) {
            List<EntityID> path =
                    this.pathPlanning.setFrom(agentInfo.getPosition()).setDestination(WorldUtil.convertToID(list)).getResult();
            if (path != null) {
                return new ActionMove(path);
            }
        }

        EntityID target = this.blockadeSelector.calc().getTarget();
        if(target != null) {
            Action action = moduleManager.getExtAction(SampleModuleKey.POLICE_ACTION_EXT_CLEAR).setTarget(target).calc().getAction();
            if(action != null) {
                return action;
            }
        }
        // Nothing to do
        target = this.search.calc().getTarget();
        return moduleManager.getExtAction(SampleModuleKey.POLICE_ACTION_SEARCH).setTarget(target).calc().getAction();
    }
}
