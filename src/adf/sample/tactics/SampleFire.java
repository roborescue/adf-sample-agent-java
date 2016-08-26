package adf.sample.tactics;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.action.fire.ActionExtinguish;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.BuildingSelector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsFire;
import adf.sample.SampleModuleKey;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class SampleFire extends TacticsFire {

    private PathPlanning pathPlanning;
    private BuildingSelector buildingSelector;
    private Search search;
    private Clustering clustering;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.GAS_STATION,
                StandardEntityURN.AMBULANCE_CENTRE,
                StandardEntityURN.FIRE_STATION,
                StandardEntityURN.POLICE_OFFICE
        );
        //init ExtAction
        moduleManager.getExtAction(SampleModuleKey.FIRE_ACTION_FIREFIGHTING, "adf.sample.extaction.ActionFireFighting");
        moduleManager.getExtAction(SampleModuleKey.FIRE_ACTION_REFILL, "adf.sample.extaction.ActionRefill");
        moduleManager.getExtAction(SampleModuleKey.FIRE_ACTION_SEARCH, "adf.sample.extaction.ActionSearch");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.precompute(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.precompute(precomputeData);
        this.buildingSelector = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_BUILDING_SELECTOR, "adf.sample.module.complex.SampleBuildingSelector");
        this.buildingSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.resume(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.resume(precomputeData);
        this.buildingSelector = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_BUILDING_SELECTOR, "adf.sample.module.complex.SampleBuildingSelector");
        this.buildingSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.preparate();
        this.clustering = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.preparate();
        this.search = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.preparate();
        this.buildingSelector = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_BUILDING_SELECTOR, "adf.sample.module.complex.SampleBuildingSelector");
        this.buildingSelector.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.buildingSelector.updateInfo(messageManager);
        this.search.updateInfo(messageManager);


        FireBrigade me = (FireBrigade) agentInfo.me();
        // Are we currently filling with water?
        // Are we out of water?
        Action action = moduleManager
                .getExtAction(SampleModuleKey.FIRE_ACTION_REFILL)
                .calc().getAction();
        if(action != null) {
            CommunicationMessage message = this.getActionMessage(me, action, true);
            if(message != null) { messageManager.addMessage(message); }
            return action;
        }

        // Find all buildings that are on fire
        Building targetBuilding = this.buildingSelector.calc().getTargetEntity();
        if(targetBuilding != null) {
            action = moduleManager
                    .getExtAction(SampleModuleKey.FIRE_ACTION_FIREFIGHTING)
                    .setTarget(targetBuilding.getID())
                    .calc().getAction();
            if(action != null) {
                CommunicationMessage message = this.getActionMessage(me, action, false);
                if(message != null) { messageManager.addMessage(message); }
                return action;
            }
        }

        // Nothing to do
        action = moduleManager
                .getExtAction(SampleModuleKey.FIRE_ACTION_SEARCH)
                .setTarget(this.search.calc().getTarget())
                .calc().getAction();
        if(action != null) {
            CommunicationMessage message = this.getActionMessage(me, action, false);
            if(message != null) { messageManager.addMessage(message); }
            return action;
        }

        //check buriedness
        if(me.getBuriedness() > 0) {
            messageManager.addMessage(
                    new MessageFireBrigade(
                            true, me,
                            MessageFireBrigade.ACTION_REST,
                            me.getPosition())
            );
        }
        return new ActionRest();
    }

    private CommunicationMessage getActionMessage(FireBrigade fireBrigade, Action action, boolean isRefill) {
        Class<? extends Action> actionClass = action.getClass();
        int actionIndex = -1;
        EntityID target = null;
        if (actionClass == ActionMove.class) {
            List<EntityID> path = ((ActionMove) action).getPath();
            actionIndex = MessageFireBrigade.ACTION_MOVE;
            target = path.get(path.size() - 1);
        } else if(actionClass == ActionExtinguish.class) {
            actionIndex = MessageFireBrigade.ACTION_EXTINGUISH;
            target = ((ActionExtinguish)action).getTarget();
        } else if(actionClass == ActionRest.class) {
            actionIndex = isRefill ? MessageFireBrigade.ACTION_REFILL : MessageFireBrigade.ACTION_REST;
            target = fireBrigade.getPosition();
        }
        return actionIndex != -1 ?
                new MessageFireBrigade(true, fireBrigade, actionIndex, target) :
                null;
    }
}
