package adf.sample.tactics;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.action.police.ActionClear;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.communication.standard.bundle.topdown.CommandPolice;
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
import adf.component.module.complex.RoadSelector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsPolice;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

public class SamplePolice extends TacticsPolice {
    private static final int ACTION_UNKNOWN = -1;
    private static final int ACTION_REST = CommandPolice.ACTION_REST;
    private static final int ACTION_MOVE = CommandPolice.ACTION_MOVE;
    private static final int ACTION_CLEAR = CommandPolice.ACTION_CLEAR;
    private static final int ACTION_SCOUT = 5;

    private int clearDistance;

    private int commandType;
    private EntityID target;
    private Collection<EntityID> scoutTargets;
    private EntityID commanderID;

    private PathPlanning pathPlanning;
    private RoadSelector roadSelector;
    private Search search;

    private ExtAction actionExtClear;
    private ExtAction actionExtMove;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.BLOCKADE
        );
        // init value
        this.clearDistance = scenarioInfo.getClearRepairDistance();
        this.commandType = ACTION_UNKNOWN;
        // init Algorithm Module & ExtAction
        switch  (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("TacticsPolice.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.search = moduleManager.getModule("TacticsPolice.Search", "adf.sample.module.complex.SampleSearch");
                this.roadSelector = moduleManager.getModule("TacticsPolice.RoadSelector", "adf.sample.module.complex.SampleRoadSelector");
                this.actionExtClear = moduleManager.getExtAction("TacticsPolice.ActionExtClear", "adf.sample.extaction.ActionExtClear");
                this.actionExtMove = moduleManager.getExtAction("TacticsPolice.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                break;
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("TacticsPolice.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.search = moduleManager.getModule("TacticsPolice.Search", "adf.sample.module.complex.SampleSearch");
                this.roadSelector = moduleManager.getModule("TacticsPolice.RoadSelector", "adf.sample.module.complex.SampleRoadSelector");
                this.actionExtClear = moduleManager.getExtAction("TacticsPolice.ActionExtClear", "adf.sample.extaction.ActionExtClear");
                this.actionExtMove = moduleManager.getExtAction("TacticsPolice.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("TacticsPolice.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.search = moduleManager.getModule("TacticsPolice.Search", "adf.sample.module.complex.SampleSearch");
                this.roadSelector = moduleManager.getModule("TacticsPolice.RoadSelector", "adf.sample.module.complex.SampleRoadSelector");
                this.actionExtClear = moduleManager.getExtAction("TacticsPolice.ActionExtClear", "adf.sample.extaction.ActionExtClear");
                this.actionExtMove = moduleManager.getExtAction("TacticsPolice.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                break;
        }
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning.precompute(precomputeData);
        this.search.precompute(precomputeData);
        this.roadSelector.precompute(precomputeData);
        this.actionExtClear.precompute(precomputeData);
        this.actionExtMove.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning.resume(precomputeData);
        this.search.resume(precomputeData);
        this.roadSelector.resume(precomputeData);
        this.actionExtClear.resume(precomputeData);
        this.actionExtMove.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        this.pathPlanning.preparate();
        this.search.preparate();
        this.roadSelector.preparate();
        this.actionExtClear.preparate();
        this.actionExtMove.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.search.updateInfo(messageManager);
        this.pathPlanning.updateInfo(messageManager);
        this.roadSelector.updateInfo(messageManager);
        this.actionExtClear.updateInfo(messageManager);
        this.actionExtMove.updateInfo(messageManager);

        PoliceForce agent = (PoliceForce) agentInfo.me();
        // command
        Action action = this.getCommandAction(agentInfo, worldInfo, messageManager);
        if(action != null) {
            this.sendActionMessage(worldInfo, messageManager, agent, action);
            return action;
        }
        // autonomous
        EntityID target = this.roadSelector.calc().getTarget();
        action = this.actionExtClear.setTarget(target).calc().getAction();
        if(action != null) {
            this.sendActionMessage(worldInfo, messageManager, agent, action);
            return action;
        }

        target = this.search.calc().getTarget();
        action = this.actionExtMove.setTarget(target).calc().getAction();
        if(action != null) {
            this.sendActionMessage(worldInfo, messageManager, agent, action);
            return action;
        }

        messageManager.addMessage(
                new MessagePoliceForce(true, agent, MessagePoliceForce.ACTION_REST, agent.getPosition())
        );
        return new ActionRest();
    }

    private Action getCommandAction(AgentInfo agentInfo, WorldInfo worldInfo, MessageManager messageManager) {
        if(this.isCommandCompleted(agentInfo, worldInfo)) {
            if(this.commandType != ACTION_UNKNOWN) {
                messageManager.addMessage(new MessageReport(true, true, false, this.commanderID));
            }
            this.commandType = ACTION_UNKNOWN;
            this.target = null;
            this.scoutTargets = null;
            this.commanderID = null;
        }
        EntityID agentID = agentInfo.getID();
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandScout.class)) {
            CommandScout command = (CommandScout) message;

            if(command.isBroadcast() || command.getToID().getValue() != agentID.getValue()) {
                continue;
            }
            this.commandType = ACTION_SCOUT;
            this.commanderID = command.getSenderID();
            this.scoutTargets = new HashSet<>();
            for(StandardEntity e : worldInfo.getObjectsInRange(command.getTargetID(), command.getRange())) {
                if(e instanceof Area && e.getStandardURN() != REFUGE) {
                    this.scoutTargets.add(e.getID());
                }
            }
            break;
        }
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandPolice.class)) {
            CommandPolice command = (CommandPolice) message;
            if(command.isToIDDefined() && command.getToID().getValue() == agentID.getValue()) {
                this.commandType = command.getAction();
                this.target = command.getTargetID();
                this.commanderID = command.getSenderID();
                break;
            }
        }
        EntityID position = agentInfo.getPosition();
        switch (this.commandType) {
            case ACTION_REST:
                if(this.target == null) {
                    if(worldInfo.getEntity(position).getStandardURN() == REFUGE) {
                        return new ActionRest();
                    }
                    this.pathPlanning.setFrom(position);
                    this.pathPlanning.setDestination(worldInfo.getEntityIDsOfType(REFUGE));
                    List<EntityID> path = this.pathPlanning.calc().getResult();
                    if(path != null && path.size() > 0) {
                        return new ActionMove(path);
                    }
                }
                else if (position.getValue() != this.target.getValue()) {
                    List<EntityID> path = this.pathPlanning.getResult(position, this.target);
                    if (path != null) {
                        return new ActionMove(path);
                    }
                }
                return new ActionRest();
            case ACTION_MOVE:
                return this.actionExtClear.setTarget(this.target).calc().getAction();
            case ACTION_CLEAR:
                return this.actionExtClear.setTarget(this.target).calc().getAction();
            case ACTION_SCOUT:
                if(this.scoutTargets == null || this.scoutTargets.isEmpty()) {
                    return null;
                }
                this.pathPlanning.setFrom(position);
                this.pathPlanning.setDestination(this.scoutTargets);
                List<EntityID> path = this.pathPlanning.calc().getResult();
                if(path != null && path.size() > 0) {
                    Action action = this.actionExtClear.setTarget(path.get(path.size() - 1)).calc().getAction();
                    if(action == null) {
                        action = new ActionMove(path);
                    }
                    return action;
                }
        }
        return null;
    }

    private boolean isCommandCompleted(AgentInfo agentInfo, WorldInfo worldInfo) {
        EntityID position = agentInfo.getPosition();
        switch (this.commandType) {
            case ACTION_REST:
                PoliceForce agent = (PoliceForce) agentInfo.me();
                if (this.target == null) {
                    return (agent.getDamage() == 0);
                }
                if (worldInfo.getEntity(this.target).getStandardURN() == REFUGE) {
                    if (agent.getPosition().getValue() == this.target.getValue()) {
                        return (agent.getDamage() == 0);
                    }
                }
                return false;
            case ACTION_MOVE:
                return this.target == null || position.getValue() == this.target.getValue();
            case ACTION_CLEAR:
                if(this.target == null) {
                    return true;
                }
                StandardEntity entity = worldInfo.getEntity(this.target);
                if (entity instanceof Road) {
                    Road road = (Road) entity;
                    if (road.isBlockadesDefined()) {
                        return road.getBlockades().isEmpty();
                    }
                    if (position.getValue() != this.target.getValue()) {
                        return false;
                    }
                } else {
                    return position.getValue() == this.target.getValue();
                }
            case ACTION_SCOUT:
                if (this.scoutTargets != null) {
                    this.scoutTargets.removeAll(worldInfo.getChanged().getChangedEntities());
                }
                return (this.scoutTargets == null || this.scoutTargets.isEmpty());
        }
        return true;
    }

    private void sendActionMessage(WorldInfo worldInfo, MessageManager messageManager, PoliceForce policeForce, Action action) {
        Class<? extends Action> actionClass = action.getClass();
        int actionIndex = -1;
        EntityID target = null;
        if(actionClass == ActionMove.class) {
            List<EntityID> path = ((ActionMove)action).getPath();
            actionIndex = MessagePoliceForce.ACTION_MOVE;
            if(path.size() > 0) {
                target = path.get(path.size() - 1);
            }
        } else if(actionClass == ActionClear.class) {
            actionIndex = MessagePoliceForce.ACTION_CLEAR;
            ActionClear ac = (ActionClear)action;
            target = ac.getTarget();
            if(target == null) {
                for(StandardEntity entity : worldInfo.getObjectsInRange(ac.getPosX(), ac.getPosY(), this.clearDistance)) {
                    if(entity.getStandardURN() == StandardEntityURN.BLOCKADE) {
                        target = entity.getID();
                        break;
                    }
                }
            }
        } else if(actionClass == ActionRest.class) {
            actionIndex = MessagePoliceForce.ACTION_REST;
            target = policeForce.getPosition();
        }
        if(actionIndex != -1) {
            messageManager.addMessage(new MessagePoliceForce(true, policeForce, actionIndex, target));
        }
    }
}
