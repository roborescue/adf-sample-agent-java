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
import adf.component.module.algorithm.Clustering;
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
    private Clustering clustering;

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
        //init ExtAction
        moduleManager.getExtAction("TacticsPolice.ActionExtClear", "adf.sample.extaction.ActionExtClear");
        moduleManager.getExtAction("TacticsPolice.ActionExtMove", "adf.sample.extaction.ActionExtMove");
        // init Algorithm Module
        switch  (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("TacticsPolice.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.clustering = moduleManager.getModule("TacticsPolice.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                this.search = moduleManager.getModule("TacticsPolice.Search", "adf.sample.module.complex.SampleSearch");
                this.roadSelector = moduleManager.getModule("TacticsPolice.RoadSelector", "adf.sample.module.complex.SampleRoadSelector");
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("TacticsPolice.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.clustering = moduleManager.getModule("TacticsPolice.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                this.search = moduleManager.getModule("TacticsPolice.Search", "adf.sample.module.complex.SampleSearch");
                this.roadSelector = moduleManager.getModule("TacticsPolice.RoadSelector", "adf.sample.module.complex.SampleRoadSelector");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("TacticsPolice.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.clustering = moduleManager.getModule("TacticsPolice.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                this.search = moduleManager.getModule("TacticsPolice.Search", "adf.sample.module.complex.SampleSearch");
                this.roadSelector = moduleManager.getModule("TacticsPolice.RoadSelector", "adf.sample.module.complex.SampleRoadSelector");
                break;
        }
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning.precompute(precomputeData);
        this.clustering.precompute(precomputeData);
        this.search.precompute(precomputeData);
        this.roadSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning.resume(precomputeData);
        this.clustering.resume(precomputeData);
        this.search.resume(precomputeData);
        this.roadSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        this.pathPlanning.preparate();
        this.clustering.preparate();
        this.search.preparate();
        this.roadSelector.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.search.updateInfo(messageManager);
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.roadSelector.updateInfo(messageManager);

        PoliceForce agent = (PoliceForce) agentInfo.me();
        // command
        this.updateTask(agentInfo, worldInfo, messageManager, true);
        if(this.commandType != ACTION_UNKNOWN) {
            Action action = this.getTaskAction(agentInfo, worldInfo, moduleManager);
            if(action != null) {
                CommunicationMessage message = this.getActionMessage(worldInfo, agent, action);
                if(message != null) {
                    messageManager.addMessage(message);
                }
            }
            return action;
        }
        // autonomous
        EntityID target = this.roadSelector.calc().getTarget();
        if(target != null) {
            Action action = moduleManager
                    .getExtAction("TacticsPolice.ActionExtClear")
                    .setTarget(target)
                    .calc().getAction();
            if(action != null) {
                CommunicationMessage message = this.getActionMessage(worldInfo, agent, action);
                if(message != null) {
                    messageManager.addMessage(message);
                }
                return action;
            }
        }
        target = this.search.calc().getTarget();
        if(target != null) {
            Action action = moduleManager
                    .getExtAction("TacticsPolice.ActionExtMove")
                    .setTarget(target)
                    .calc().getAction();
            if(action != null) {
                CommunicationMessage message = this.getActionMessage(worldInfo, agent, action);
                if(message != null) {
                    messageManager.addMessage(message);
                }
                return action;
            }
        }

        messageManager.addMessage(
                new MessagePoliceForce(true, agent, MessagePoliceForce.ACTION_REST, agent.getPosition())
        );
        return new ActionRest();
    }

    private CommunicationMessage getActionMessage(WorldInfo worldInfo, PoliceForce policeForce, Action action) {
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
            return new MessagePoliceForce(true, policeForce, actionIndex, target);
        }
        return null;
    }

    private void updateTask(AgentInfo agentInfo, WorldInfo worldInfo, MessageManager messageManager, boolean sendReport) {
        if(this.checkTask(agentInfo, worldInfo) && sendReport) {
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
            if(command.getToID().getValue() != agentID.getValue()) {
                continue;
            }
            this.commandType = ACTION_SCOUT;
            this.commanderID = command.getSenderID();
            this.scoutTargets = new HashSet<>();
            for(StandardEntity e : worldInfo.getObjectsInRange(command.getTargetID(), command.getRange())) {
                if(e instanceof Area) {
                    this.scoutTargets.add(e.getID());
                }
            }
            break;
        }
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandPolice.class)) {
            CommandPolice command = (CommandPolice) message;
            if(command.getToID().getValue() == agentID.getValue()) {
                this.commandType = command.getAction();
                this.target = command.getTargetID();
                this.commanderID = command.getSenderID();
                break;
            }
        }
    }

    private boolean checkTask(AgentInfo agentInfo, WorldInfo worldInfo) {
        if(this.commandType == ACTION_REST) {
            return this.checkRestTask(agentInfo, worldInfo);
        } else if(this.commandType == ACTION_MOVE) {
            return this.checkMoveTask(agentInfo, worldInfo);
        } else if(this.commandType == ACTION_CLEAR) {
            return this.checkClearTask(agentInfo, worldInfo);
        } else if(this.commandType == ACTION_SCOUT) {
            return this.checkScoutTask(worldInfo);
        }
        return true;
    }

    private boolean checkRestTask(AgentInfo agentInfo, WorldInfo worldInfo) {
        if(worldInfo.getEntity(this.target).getStandardURN() == REFUGE) {
            AmbulanceTeam agent = (AmbulanceTeam) agentInfo.me();
            if (agent.getPosition().getValue() == this.target.getValue()) {
                return (agent.getDamage() == 0);
            }
        }
        return false;
    }

    private boolean checkMoveTask(AgentInfo agentInfo, WorldInfo worldInfo) {
        if(this.target == null) {
            return true;
        }
        if(!(worldInfo.getEntity(this.target) instanceof Area)) {
            return true;
        }
        return (agentInfo.getPosition().getValue() == this.target.getValue());
    }

    private boolean checkScoutTask(WorldInfo worldInfo) {
        this.scoutTargets.removeAll(worldInfo.getChanged().getChangedEntities());
        return (this.scoutTargets == null || this.scoutTargets.isEmpty());
    }

    private boolean checkClearTask(AgentInfo agentInfo, WorldInfo worldInfo) {
        if(this.target == null) {
            return true;
        }
        StandardEntity entity = worldInfo.getEntity(this.target);
        if(entity instanceof Road) {
            Road road = (Road)entity;
            if(road.isBlockadesDefined()) {
                return road.getBlockades().isEmpty();
            } else {
                if(agentInfo.getPosition().getValue() != this.target.getValue()) {
                    return false;
                }
            }
        }
        return true;
    }

    private Action getTaskAction(AgentInfo agentInfo, WorldInfo worldInfo, ModuleManager moduleManager) {
        if(this.commandType == ACTION_REST) {
            return this.getRestAction(agentInfo, worldInfo);
        } else if(this.commandType == ACTION_MOVE) {
            return this.getMoveAction(agentInfo, worldInfo);
        } else if(this.commandType == ACTION_CLEAR) {
            return this.getClearTask(moduleManager);
        } else if(this.commandType == ACTION_SCOUT) {
            return this.getScoutAction(agentInfo);
        }
        return null;
    }

    private Action getRestAction(AgentInfo agentInfo, WorldInfo worldInfo) {
        EntityID position = agentInfo.getPosition();
        if(worldInfo.getEntity(this.target) instanceof Area) {
            if (position.getValue() == this.target.getValue()) {
                return new ActionRest();
            } else {
                this.pathPlanning.setFrom(position);
                this.pathPlanning.setDestination(this.target);
                List<EntityID> path = this.pathPlanning.calc().getResult();
                if(path != null) {
                    return new ActionMove(path);
                }
            }
        }
        this.pathPlanning.setFrom(position);
        this.pathPlanning.setDestination(worldInfo.getEntityIDsOfType(REFUGE));
        List<EntityID> path = this.pathPlanning.calc().getResult();
        if(path != null) {
            return new ActionMove(path);
        }
        return new ActionRest();
    }

    private Action getMoveAction(AgentInfo agentInfo, WorldInfo worldInfo) {
        if(worldInfo.getEntity(this.target) instanceof Area) {
            EntityID position = agentInfo.getPosition();
            if (position.getValue() == this.target.getValue()) {
                return new ActionRest();
            } else {
                this.pathPlanning.setFrom(position);
                this.pathPlanning.setDestination(this.target);
                List<EntityID> path = this.pathPlanning.calc().getResult();
                if(path != null) {
                    return new ActionMove(path);
                }
            }
        }
        return null;
    }

    private Action getScoutAction(AgentInfo agentInfo) {
        if(this.scoutTargets == null || this.scoutTargets.isEmpty()) {
            return null;
        }
        this.pathPlanning.setFrom(agentInfo.getPosition());
        this.pathPlanning.setDestination(this.scoutTargets);
        List<EntityID> path = this.pathPlanning.calc().getResult();
        if(path != null) {
            return new ActionMove(path);
        }
        return null;
    }

    private Action getClearTask(ModuleManager moduleManager) {
        return moduleManager
                .getExtAction("TacticsPolice.ActionExtClear")
                .setTarget(this.target)
                .calc().getAction();
    }
}
