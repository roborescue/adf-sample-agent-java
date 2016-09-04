package adf.sample.tactics;

import adf.agent.action.Action;
import adf.agent.action.ambulance.ActionLoad;
import adf.agent.action.ambulance.ActionRescue;
import adf.agent.action.ambulance.ActionUnload;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.HumanSelector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsAmbulance;
import adf.sample.SampleModuleKey;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class SampleAmbulance extends TacticsAmbulance {

    private PathPlanning pathPlanning;
    private HumanSelector humanSelector;
    private Search search;
    private Clustering clustering;

    private HumanSelector taskHumanSelector;
    private Search taskSearch;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
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
        //init ExtAction
        moduleManager.getExtAction(SampleModuleKey.AMBULANCE_ACTION_TRANSPORT, "adf.sample.extaction.ActionTransport");
        moduleManager.getExtAction(SampleModuleKey.AMBULANCE_ACTION_SEARCH, "adf.sample.extaction.ActionExtMove");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.precompute(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.precompute(precomputeData);
        this.humanSelector = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_HUMAN_SELECTOR, "adf.sample.module.complex.SampleVictimSelector");
        this.humanSelector.precompute(precomputeData);
        this.taskHumanSelector = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_TASK_HUMAN_SELECTOR, "adf.sample.module.complex.topdown.SampleTaskVictimSelector");
        this.taskHumanSelector.precompute(precomputeData);
        this.taskSearch = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_TASK_SEARCH, "adf.sample.module.complex.topdown.SampleTaskSearch");
        this.taskSearch.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.resume(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.resume(precomputeData);
        this.humanSelector = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_HUMAN_SELECTOR, "adf.sample.module.complex.SampleVictimSelector");
        this.humanSelector.resume(precomputeData);
        this.taskHumanSelector = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_TASK_HUMAN_SELECTOR, "adf.sample.module.complex.topdown.SampleTaskVictimSelector");
        this.taskHumanSelector.resume(precomputeData);
        this.taskSearch = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_TASK_SEARCH, "adf.sample.module.complex.topdown.SampleTaskSearch");
        this.taskSearch.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.preparate();
        this.clustering = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.preparate();
        this.search = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.preparate();
        this.humanSelector = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_HUMAN_SELECTOR, "adf.sample.module.complex.SampleVictimSelector");
        this.humanSelector.preparate();
        this.taskHumanSelector = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_TASK_HUMAN_SELECTOR, "adf.sample.module.complex.topdown.SampleTaskVictimSelector");
        this.taskHumanSelector.preparate();
        this.taskSearch = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_TASK_SEARCH, "adf.sample.module.complex.topdown.SampleTaskSearch");
        this.taskSearch.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.search.updateInfo(messageManager);
        this.taskSearch.updateInfo(messageManager);
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.humanSelector.updateInfo(messageManager);
        this.taskHumanSelector.updateInfo(messageManager);

        AmbulanceTeam me = (AmbulanceTeam)agentInfo.me();
        EntityID target = this.taskSearch.calc().getTarget();
        if(target != null) {
            Action action = moduleManager
                    .getExtAction(SampleModuleKey.AMBULANCE_ACTION_SEARCH)
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
        target = this.taskHumanSelector.calc().getTarget();
        if(target == null) {
            target = this.humanSelector.calc().getTarget();
        }
        if(target != null) {
            Action action = moduleManager
                    .getExtAction(SampleModuleKey.AMBULANCE_ACTION_TRANSPORT)
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
                    .getExtAction(SampleModuleKey.AMBULANCE_ACTION_SEARCH)
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

        //check buriedness
        if(me.getBuriedness() > 0) {
            messageManager.addMessage(
                    new MessageAmbulanceTeam(true, me, MessageAmbulanceTeam.ACTION_REST, me.getPosition())
            );
        }
        return new ActionRest();
    }

    private CommunicationMessage getActionMessage(AmbulanceTeam ambulance, Action action) {
        Class<? extends Action> actionClass = action.getClass();
        int actionIndex = -1;
        EntityID target = null;
        if(actionClass == ActionMove.class) {
            List<EntityID> path = ((ActionMove)action).getPath();
            actionIndex = MessageAmbulanceTeam.ACTION_MOVE;
            target = path.get(path.size() - 1);
        } else if(actionClass == ActionRescue.class) {
            actionIndex = MessageAmbulanceTeam.ACTION_RESCUE;
            target = ((ActionRescue)action).getTarget();
        } else if(actionClass == ActionLoad.class) {
            actionIndex = MessageAmbulanceTeam.ACTION_LOAD;
            target = ((ActionLoad)action).getTarget();
        } else if(actionClass == ActionUnload.class) {
            actionIndex = MessageAmbulanceTeam.ACTION_UNLOAD;
            target = ambulance.getPosition();
        } else if(actionClass == ActionRest.class) {
            actionIndex = MessageAmbulanceTeam.ACTION_REST;
            target = ambulance.getPosition();
        }
        if(actionIndex != -1) {
            return new MessageAmbulanceTeam(true, ambulance, actionIndex, target);
        }
        return null;
    }
}
