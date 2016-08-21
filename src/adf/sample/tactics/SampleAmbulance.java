package adf.sample.tactics;

import adf.agent.action.Action;
import adf.agent.action.ambulance.ActionLoad;
import adf.agent.action.ambulance.ActionRescue;
import adf.agent.action.ambulance.ActionUnload;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.debug.DebugData;
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
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class SampleAmbulance extends TacticsAmbulance {

    private PathPlanning pathPlanning;
    private HumanSelector humanSelector;
    private Search search;
    private Clustering clustering;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DebugData debugData) {
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
        moduleManager.getExtAction(SampleModuleKey.AMBULANCE_ACTION_SEARCH, "adf.sample.extaction.ActionSearch");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DebugData debugData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.precompute(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.precompute(precomputeData);
        this.humanSelector = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_HUMAN_SELECTOR, "adf.sample.module.complex.SampleVictimSelector");
        this.humanSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DebugData debugData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.resume(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.resume(precomputeData);
        this.humanSelector = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_HUMAN_SELECTOR, "adf.sample.module.complex.SampleVictimSelector");
        this.humanSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DebugData debugData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.preparate();
        this.clustering = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.preparate();
        this.search = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.preparate();
        this.humanSelector = moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_HUMAN_SELECTOR, "adf.sample.module.complex.SampleVictimSelector");
        this.humanSelector.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DebugData debugData) {
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.humanSelector.updateInfo(messageManager);
        this.search.updateInfo(messageManager);

        AmbulanceTeam me = (AmbulanceTeam)agentInfo.me();
        Human targetHuman = agentInfo.someoneOnBoard();
        if(targetHuman == null) {
            targetHuman = this.humanSelector.calc().getTargetEntity();
        }
        if(targetHuman != null) {
            Action action = moduleManager
                    .getExtAction(SampleModuleKey.AMBULANCE_ACTION_TRANSPORT)
                    .setTarget(targetHuman.getID())
                    .calc().getAction();
            if(action != null) {
                CommunicationMessage message = this.getActionMessage(me, action);
                if(message != null) { messageManager.addMessage(message); }
                return action;
            }
        }

        // Nothing to do
        Action action = moduleManager
                .getExtAction(SampleModuleKey.AMBULANCE_ACTION_SEARCH)
                .setTarget(this.search.calc().getTarget())
                .calc().getAction();
        if(action != null) {
            CommunicationMessage message = this.getActionMessage(me, action);
            if(message != null) { messageManager.addMessage(message); }
            return action;
        }

        //check buriedness
        if(me.getBuriedness() > 0) {
            messageManager.addMessage(
                    new MessageAmbulanceTeam(
                            true, me,
                            MessageAmbulanceTeam.ACTION_REST,
                            me.getPosition())
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
        return actionIndex != -1 ?
                new MessageAmbulanceTeam(true, ambulance, actionIndex, target) :
                null;
    }
}
