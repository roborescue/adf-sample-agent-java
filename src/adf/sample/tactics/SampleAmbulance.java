package adf.sample.tactics;

import adf.agent.action.Action;
import adf.agent.action.ambulance.ActionLoad;
import adf.agent.action.ambulance.ActionRescue;
import adf.agent.action.ambulance.ActionUnload;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.communication.standard.bundle.topdown.CommandAmbulance;
import adf.agent.communication.standard.bundle.topdown.CommandScout;
import adf.agent.communication.standard.bundle.topdown.MessageReport;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.HumanSelector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsAmbulance;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.AMBULANCE_TEAM;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

public class SampleAmbulance extends TacticsAmbulance {
    private static final int ACTION_UNKNOWN = -1;
    private static final int ACTION_REST = CommandAmbulance.ACTION_REST;
    private static final int ACTION_MOVE = CommandAmbulance.ACTION_MOVE;
    private static final int ACTION_RESCUE = CommandAmbulance.ACTION_RESCUE;
    private static final int ACTION_LOAD = CommandAmbulance.ACTION_LOAD;
    private static final int ACTION_UNLOAD = CommandAmbulance.ACTION_UNLOAD;
    private static final int ACTION_SCOUT = 5;

    private int type;
    private EntityID target;
    private Collection<EntityID> scoutTargets;
    private EntityID commanderID;

    private PathPlanning pathPlanning;
    private HumanSelector humanSelector;
    private Search search;

    private ExtAction actionTransport;
    private ExtAction actionExtMove;

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
        // init value
        this.type = ACTION_UNKNOWN;
        // init Algorithm Module & ExtAction
        switch  (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("TacticsAmbulance.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.humanSelector = moduleManager.getModule("TacticsAmbulance.HumanSelector", "adf.sample.module.complex.SampleVictimSelector");
                this.search = moduleManager.getModule("TacticsAmbulance.Search", "adf.sample.module.complex.SampleSearch");
                this.actionTransport = moduleManager.getExtAction("TacticsAmbulance.ActionTransport", "adf.sample.extaction.ActionTransport");
                this.actionExtMove = moduleManager.getExtAction("TacticsAmbulance.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                break;
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("TacticsAmbulance.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.humanSelector = moduleManager.getModule("TacticsAmbulance.HumanSelector", "adf.sample.module.complex.SampleVictimSelector");
                this.search = moduleManager.getModule("TacticsAmbulance.Search", "adf.sample.module.complex.SampleSearch");
                this.actionTransport = moduleManager.getExtAction("TacticsAmbulance.ActionTransport", "adf.sample.extaction.ActionTransport");
                this.actionExtMove = moduleManager.getExtAction("TacticsAmbulance.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("TacticsAmbulance.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.humanSelector = moduleManager.getModule("TacticsAmbulance.HumanSelector", "adf.sample.module.complex.SampleVictimSelector");
                this.search = moduleManager.getModule("TacticsAmbulance.Search", "adf.sample.module.complex.SampleSearch");
                this.actionTransport = moduleManager.getExtAction("TacticsAmbulance.ActionTransport", "adf.sample.extaction.ActionTransport");
                this.actionExtMove = moduleManager.getExtAction("TacticsAmbulance.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                break;
        }
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning.precompute(precomputeData);
        this.humanSelector.precompute(precomputeData);
        this.search.precompute(precomputeData);
        this.actionTransport.precompute(precomputeData);
        this.actionExtMove.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning.resume(precomputeData);
        this.humanSelector.resume(precomputeData);
        this.search.resume(precomputeData);
        this.actionTransport.resume(precomputeData);
        this.actionExtMove.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        this.pathPlanning.preparate();
        this.humanSelector.preparate();
        this.search.preparate();
        this.actionTransport.preparate();
        this.actionExtMove.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.search.updateInfo(messageManager);
        this.pathPlanning.updateInfo(messageManager);
        this.humanSelector.updateInfo(messageManager);
        this.actionTransport.updateInfo(messageManager);
        this.actionExtMove.updateInfo(messageManager);

        AmbulanceTeam agent = (AmbulanceTeam)agentInfo.me();
        // command
        Action action = this.getCommandAction(agentInfo, worldInfo, moduleManager, messageManager);
        if(action != null) {
            this.sendActionMessage(messageManager, agent, action);
            return action;
        }
        // autonomous
        EntityID target = this.humanSelector.calc().getTarget();
        action = this.actionTransport.setTarget(target).calc().getAction();
        if(action != null) {
            this.sendActionMessage(messageManager, agent, action);
            return action;
        }
        target = this.search.calc().getTarget();
        action = this.actionExtMove.setTarget(target).calc().getAction();
        if(action != null) {
            this.sendActionMessage(messageManager, agent, action);
            return action;
        }

        messageManager.addMessage(
                new MessageAmbulanceTeam(true, agent, MessageAmbulanceTeam.ACTION_REST, agent.getPosition())
        );
        return new ActionRest();
    }

    private Action getCommandAction(AgentInfo agentInfo, WorldInfo worldInfo, ModuleManager moduleManager, MessageManager messageManager) {
        AmbulanceTeam agent = (AmbulanceTeam)agentInfo.me();
        if(this.isCommandCompleted(agentInfo, worldInfo)) {
            if(this.type != ACTION_UNKNOWN) {
                messageManager.addMessage(new MessageReport(true, true, false, this.commanderID));
            }
            this.type = ACTION_UNKNOWN;
        }
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandScout.class)) {
            CommandScout command = (CommandScout) message;
            if(command.getToID().getValue() == agent.getID().getValue()) {
                this.type = ACTION_SCOUT;
                this.commanderID = command.getSenderID();
                this.scoutTargets = new HashSet<>();
                for (StandardEntity e : worldInfo.getObjectsInRange(command.getTargetID(), command.getRange())) {
                    if (e instanceof Area && e.getStandardURN() != REFUGE) {
                        this.scoutTargets.add(e.getID());
                    }
                }
                break;
            }
        }
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandAmbulance.class)) {
            CommandAmbulance command = (CommandAmbulance) message;
            if(command.getToID().getValue() == agent.getID().getValue()) {
                this.type = command.getAction();
                this.target = command.getTargetID();
                this.commanderID = command.getSenderID();
                break;
            }
        }
        switch (this.type) {
            case ACTION_REST:
                EntityID position = agentInfo.getPosition();
                if (position.getValue() != this.target.getValue()) {
                    List<EntityID> path = this.pathPlanning.getResult(position, this.target);
                    if(path != null) {
                        return new ActionMove(path);
                    }
                }
                return new ActionRest();
            case ACTION_MOVE:
                return moduleManager.getExtAction("TacticsAmbulance.ActionExtMove")
                        .setTarget(this.target)
                        .calc().getAction();
            case ACTION_RESCUE:
                return moduleManager.getExtAction("TacticsAmbulance.ActionTransport")
                        .setTarget(this.target)
                        .calc().getAction();
            case ACTION_LOAD:
                return moduleManager.getExtAction("TacticsAmbulance.ActionTransport")
                        .setTarget(this.target)
                        .calc().getAction();
            case ACTION_UNLOAD:
                return moduleManager.getExtAction("TacticsAmbulance.ActionTransport")
                        .setTarget(this.target)
                        .calc().getAction();
            case ACTION_SCOUT:
                if(this.scoutTargets == null || this.scoutTargets.isEmpty()) {
                    return null;
                }
                this.pathPlanning.setFrom(agentInfo.getPosition());
                this.pathPlanning.setDestination(this.scoutTargets);
                List<EntityID> path = this.pathPlanning.calc().getResult();
                if(path != null) {
                    return new ActionMove(path);
                }
        }
        return null;
    }

    private boolean isCommandCompleted(AgentInfo agentInfo, WorldInfo worldInfo) {
        switch (this.type) {
            case ACTION_REST:
                if (worldInfo.getEntity(this.target).getStandardURN() == REFUGE) {
                    Human agent = (Human) agentInfo.me();
                    return (agent.getDamage() == 0);
                }
                return false;
            case ACTION_MOVE:
                return (agentInfo.getPosition().getValue() == this.target.getValue());
            case ACTION_RESCUE:
                Human human = (Human) worldInfo.getEntity(this.target);
                if (human.isBuriednessDefined() && human.getBuriedness() == 0) {
                    return true;
                }
                return (human.isHPDefined() && human.getHP() == 0);
            case ACTION_LOAD:
                Human human1 = (Human) worldInfo.getEntity(this.target);
                if (human1.isPositionDefined()) {
                    EntityID position = human1.getPosition();
                    if (worldInfo.getEntityIDsOfType(AMBULANCE_TEAM).contains(position)) {
                        return true;
                    } else if (worldInfo.getEntity(position).getStandardURN() == REFUGE) {
                        return true;
                    }
                }
                return false;
            case ACTION_UNLOAD:
                StandardEntity entity = worldInfo.getEntity(this.target);
                if (entity instanceof Area) {
                    if (this.target.getValue() != agentInfo.getPosition().getValue()) {
                        return false;
                    }
                }
                return (agentInfo.someoneOnBoard() == null);
            case ACTION_SCOUT:
                this.scoutTargets.removeAll(worldInfo.getChanged().getChangedEntities());
                return (this.scoutTargets == null || this.scoutTargets.isEmpty());
        }
        return true;
    }

    private void sendActionMessage(MessageManager messageManager, AmbulanceTeam ambulance, Action action) {
        Class<? extends Action> actionClass = action.getClass();
        int actionIndex = -1;
        EntityID target = null;
        if(actionClass == ActionMove.class) {
            actionIndex = MessageAmbulanceTeam.ACTION_MOVE;
            List<EntityID> path = ((ActionMove)action).getPath();
            if(path.size() > 0) {
                target = path.get(path.size() - 1);
            }
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
            messageManager.addMessage(new MessageAmbulanceTeam(true, ambulance, actionIndex, target));
        }
    }
}
