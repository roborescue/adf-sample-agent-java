package adf.sample.tactics;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.action.fire.ActionExtinguish;
import adf.agent.action.fire.ActionRefill;
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
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class SampleFire extends TacticsFire {

    private PathPlanning pathPlanning;
    private BuildingSelector buildingSelector;
    private Search search;
    private Clustering clustering;

    private BuildingSelector taskBuildingSelector;
    private Search taskSearch;

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
        moduleManager.getExtAction("TacticsFire.ActionFireFighting", "adf.sample.extaction.ActionFireFighting");
        moduleManager.getExtAction("TacticsFire.ActionExtMove", "adf.sample.extaction.ActionExtMove");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule("TacticsFire.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule("TacticsFire.Clustering", "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.precompute(precomputeData);
        this.search = moduleManager.getModule("TacticsFire.Search", "adf.sample.module.complex.SampleSearch");
        this.search.precompute(precomputeData);
        this.buildingSelector = moduleManager.getModule("TacticsFire.BuildingSelector", "adf.sample.module.complex.SampleBuildingSelector");
        this.buildingSelector.precompute(precomputeData);
        this.taskBuildingSelector = moduleManager.getModule("TacticsFire.TaskBuildingSelector", "adf.sample.module.complex.topdown.SampleTaskBuildingSelector");
        this.taskBuildingSelector.precompute(precomputeData);
        this.taskSearch = moduleManager.getModule("TacticsFire.TaskSearch", "adf.sample.module.complex.topdown.SampleTaskSearch");
        this.taskSearch.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule("TacticsFire.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule("TacticsFire.Clustering", "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.resume(precomputeData);
        this.search = moduleManager.getModule("TacticsFire.Search", "adf.sample.module.complex.SampleSearch");
        this.search.resume(precomputeData);
        this.buildingSelector = moduleManager.getModule("TacticsFire.BuildingSelector", "adf.sample.module.complex.SampleBuildingSelector");
        this.buildingSelector.resume(precomputeData);
        this.taskBuildingSelector = moduleManager.getModule("TacticsFire.TaskBuildingSelector", "adf.sample.module.complex.topdown.SampleTaskBuildingSelector");
        this.taskBuildingSelector.resume(precomputeData);
        this.taskSearch = moduleManager.getModule("TacticsFire.TaskSearch", "adf.sample.module.complex.topdown.SampleTaskSearch");
        this.taskSearch.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule("TacticsFire.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.preparate();
        this.clustering = moduleManager.getModule("TacticsFire.Clustering", "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.preparate();
        this.search = moduleManager.getModule("TacticsFire.Search", "adf.sample.module.complex.SampleSearch");
        this.search.preparate();
        this.buildingSelector = moduleManager.getModule("TacticsFire.BuildingSelector", "adf.sample.module.complex.SampleBuildingSelector");
        this.buildingSelector.preparate();
        this.taskBuildingSelector = moduleManager.getModule("TacticsFire.TaskBuildingSelector", "adf.sample.module.complex.topdown.SampleTaskBuildingSelector");
        this.taskBuildingSelector.preparate();
        this.taskSearch = moduleManager.getModule("TacticsFire.TaskSearch", "adf.sample.module.complex.topdown.SampleTaskSearch");
        this.taskSearch.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.search.updateInfo(messageManager);
        this.taskSearch.updateInfo(messageManager);
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.buildingSelector.updateInfo(messageManager);
        this.taskBuildingSelector.updateInfo(messageManager);

        FireBrigade me = (FireBrigade) agentInfo.me();
        EntityID target = this.taskSearch.calc().getTarget();
        if(target != null) {
            Action action = moduleManager
                    .getExtAction("TacticsFire.ActionExtMove")
                    .setTarget(this.search.calc().getTarget())
                    .calc().getAction();
            if(action != null) {
                CommunicationMessage message = this.getActionMessage(me, action);
                if(message != null) {
                    messageManager.addMessage(message);
                }
                return action;
            }
        }
        target = this.taskBuildingSelector.calc().getTarget();
        if(target == null) {
            target = this.buildingSelector.calc().getTarget();
        }
        if(target != null) {
            Action action = moduleManager
                    .getExtAction("TacticsFire.ActionFireFighting")
                    .setTarget(target)
                    .calc().getAction();
            if(action != null) {
                CommunicationMessage message = this.getActionMessage(me, action);
                if(message != null) {
                    messageManager.addMessage(message);
                }
                return action;
            }
        }
        target = this.search.calc().getTarget();
        if(target != null) {
            Action action = moduleManager
                    .getExtAction("TacticsFire.ActionExtMove")
                    .setTarget(this.search.calc().getTarget())
                    .calc().getAction();
            if(action != null) {
                CommunicationMessage message = this.getActionMessage(me, action);
                if(message != null) {
                    messageManager.addMessage(message);
                }
                return action;
            }
        }

        //check buriedness
        if(me.getBuriedness() > 0) {
            messageManager.addMessage(
                    new MessageFireBrigade(true, me, MessageFireBrigade.ACTION_REST,  me.getPosition())
            );
        }
        return new ActionRest();
    }

    private CommunicationMessage getActionMessage(FireBrigade fireBrigade, Action action) {
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
        } else if(actionClass == ActionRefill.class) {
            actionIndex = MessageFireBrigade.ACTION_REFILL;
            target = fireBrigade.getPosition();
        } else if(actionClass == ActionRest.class) {
            actionIndex = MessageFireBrigade.ACTION_REST;
            target = fireBrigade.getPosition();
        }
        if(actionIndex != -1) {
            return new MessageFireBrigade(true, fireBrigade, actionIndex, target);
        }
        return null;
    }
}
