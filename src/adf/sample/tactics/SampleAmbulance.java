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
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.HumanSelector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsAmbulance;

import rescuecore2.standard.entities.*;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.AMBULANCE_TEAM;
import static rescuecore2.standard.entities.StandardEntityURN.CIVILIAN;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

public class SampleAmbulance extends TacticsAmbulance {
    private static final int ACTION_UNKNOWN = -1;
    private static final int ACTION_REST = CommandAmbulance.ACTION_REST;
    private static final int ACTION_MOVE = CommandAmbulance.ACTION_MOVE;
    private static final int ACTION_RESCUE = CommandAmbulance.ACTION_RESCUE;
    private static final int ACTION_LOAD = CommandAmbulance.ACTION_LOAD;
    private static final int ACTION_UNLOAD = CommandAmbulance.ACTION_UNLOAD;
    private static final int ACTION_SCOUT = 5;

    private int task;
    private EntityID target;
    private Collection<EntityID> scoutTargets;
    private EntityID commanderID;

    private PathPlanning pathPlanning;
    private HumanSelector humanSelector;
    private Search search;
    private Clustering clustering;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.task = ACTION_UNKNOWN;
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
        moduleManager.getExtAction("TacticsAmbulance.ActionTransport", "adf.sample.extaction.ActionTransport");
        moduleManager.getExtAction("TacticsAmbulance.ActionExtMove", "adf.sample.extaction.ActionExtMove");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule("TacticsAmbulance.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule("TacticsAmbulance.Clustering", "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.precompute(precomputeData);
        this.search = moduleManager.getModule("TacticsAmbulance.Search", "adf.sample.module.complex.SampleSearch");
        this.search.precompute(precomputeData);
        this.humanSelector = moduleManager.getModule("TacticsAmbulance.HumanSelector", "adf.sample.module.complex.SampleVictimSelector");
        this.humanSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule("TacticsAmbulance.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule("TacticsAmbulance.Clustering", "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.resume(precomputeData);
        this.search = moduleManager.getModule("TacticsAmbulance.Search", "adf.sample.module.complex.SampleSearch");
        this.search.resume(precomputeData);
        this.humanSelector = moduleManager.getModule("TacticsAmbulance.HumanSelector", "adf.sample.module.complex.SampleVictimSelector");
        this.humanSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule("TacticsAmbulance.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.preparate();
        this.clustering = moduleManager.getModule("TacticsAmbulance.Clustering", "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.preparate();
        this.search = moduleManager.getModule("TacticsAmbulance.Search", "adf.sample.module.complex.SampleSearch");
        this.search.preparate();
        this.humanSelector = moduleManager.getModule("TacticsAmbulance.HumanSelector", "adf.sample.module.complex.SampleVictimSelector");
        this.humanSelector.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.search.updateInfo(messageManager);
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.humanSelector.updateInfo(messageManager);

        this.updateTask(agentInfo, worldInfo, messageManager, true);
        if(this.task != ACTION_UNKNOWN) {
            return this.getTaskAction(agentInfo, worldInfo, moduleManager);
        }

        AmbulanceTeam agent = (AmbulanceTeam)agentInfo.me();
        EntityID target = this.humanSelector.calc().getTarget();
        if(target != null) {
            Action action = moduleManager
                    .getExtAction("TacticsAmbulance.ActionTransport")
                    .setTarget(target)
                    .calc().getAction();
            if(action != null) {
                CommunicationMessage message = this.getActionMessage(agent, action);
                if(message != null) {
                    messageManager.addMessage(message);
                }
                return action;
            }
        }
        target = this.search.calc().getTarget();
        if(target != null) {
            Action action = moduleManager
                    .getExtAction("TacticsAmbulance.ActionExtMove")
                    .setTarget(target)
                    .calc().getAction();
            if(action != null) {
                CommunicationMessage message = this.getActionMessage(agent, action);
                if(message != null) {
                    messageManager.addMessage(message);
                }
                return action;
            }
        }

        //check buriedness
        if(agent.getBuriedness() > 0) {
            messageManager.addMessage(
                    new MessageAmbulanceTeam(true, agent, MessageAmbulanceTeam.ACTION_REST, agent.getPosition())
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

    private void updateTask(AgentInfo agentInfo, WorldInfo worldInfo, MessageManager messageManager, boolean sendReport) {
        if(this.checkTask(agentInfo, worldInfo) && sendReport) {
            if(this.task != ACTION_UNKNOWN) {
                messageManager.addMessage(new MessageReport(true, true, false, this.commanderID));
            }
            this.task = ACTION_UNKNOWN;
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
            this.task = ACTION_SCOUT;
            this.commanderID = command.getSenderID();
            this.scoutTargets = new HashSet<>();
            for(StandardEntity e : worldInfo.getObjectsInRange(command.getTargetID(), command.getRange())) {
                if(e instanceof Area) {
                    this.scoutTargets.add(e.getID());
                }
            }
            break;
        }
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandAmbulance.class)) {
            CommandAmbulance command = (CommandAmbulance) message;
            if(command.getToID().getValue() == agentID.getValue()) {
                this.task = command.getAction();
                this.target = command.getTargetID();
                this.commanderID = command.getSenderID();
                break;
            }
        }
    }

    private boolean checkTask(AgentInfo agentInfo, WorldInfo worldInfo) {
        if(this.task == ACTION_REST) {
            return this.checkRestTask(agentInfo, worldInfo);
        } else if(this.task == ACTION_MOVE) {
            return this.checkMoveTask(agentInfo, worldInfo);
        } else if(this.task == ACTION_RESCUE) {
            return this.checkRescueTask(worldInfo);
        } else if(this.task == ACTION_LOAD) {
            return this.checkLoadTask(worldInfo);
        } else if(this.task == ACTION_UNLOAD) {
            return this.checkUnloadTask(agentInfo, worldInfo);
        } else if(this.task == ACTION_SCOUT) {
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

    private boolean checkRescueTask(WorldInfo worldInfo) {
        if(this.target == null) {
            return true;
        }
        StandardEntity entity = worldInfo.getEntity(this.target);
        if(entity instanceof Human) {
            Human human = (Human) entity;
            return (human.isBuriednessDefined() && human.getBuriedness() == 0);
        }
        return true;
    }

    private boolean checkLoadTask(WorldInfo worldInfo) {
        if(this.target == null) {
            return true;
        }
        StandardEntity entity = worldInfo.getEntity(this.target);
        if(!(entity instanceof Human)) {
            return true;
        }
        if(entity.getStandardURN() == StandardEntityURN.CIVILIAN) {
            Human civilian = (Human)entity;
            if(civilian.isPositionDefined()) {
                EntityID position = civilian.getPosition();
                if(worldInfo.getEntity(position).getStandardURN() == REFUGE) {
                    return true;
                }
                if (worldInfo.getEntityIDsOfType(AMBULANCE_TEAM).contains(position)) {
                    return true;
                }
            }
        } else {
            Human human = (Human)entity;
            if(human.isBuriednessDefined() && human.getBuriedness() == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean checkUnloadTask(AgentInfo agentInfo, WorldInfo worldInfo) {
        StandardEntity entity = worldInfo.getEntity(this.target);
        if(entity instanceof Area) {
            if(this.target.getValue() != agentInfo.getPosition().getValue()) {
                return false;
            }
        }
        return (agentInfo.someoneOnBoard() == null);
    }

    private boolean checkScoutTask(WorldInfo worldInfo) {
        this.scoutTargets.removeAll(worldInfo.getChanged().getChangedEntities());
        return (this.scoutTargets == null || this.scoutTargets.isEmpty());
    }

    private Action getTaskAction(AgentInfo agentInfo, WorldInfo worldInfo, ModuleManager moduleManager) {
        if(this.task == ACTION_REST) {
            return this.getRestAction(agentInfo, worldInfo, moduleManager);
        } else if(this.task == ACTION_MOVE) {
            return this.getMoveAction(agentInfo, worldInfo, moduleManager);
        } else if(this.task == ACTION_RESCUE) {
            return this.getRescueTask(agentInfo, worldInfo, moduleManager);
        } else if(this.task == ACTION_LOAD) {
            return this.getLoadTask(agentInfo, worldInfo, moduleManager);
        } else if(this.task == ACTION_UNLOAD) {
            return this.getUnloadTask(agentInfo, worldInfo, moduleManager);
        } else if(this.task == ACTION_SCOUT) {
            return this.getScoutAction(agentInfo, moduleManager);
        }
        return null;
    }

    private Action getRestAction(AgentInfo agentInfo, WorldInfo worldInfo, ModuleManager moduleManager) {
        if(worldInfo.getEntity(this.target) instanceof Area) {
            if (agentInfo.getPosition().getValue() == this.target.getValue()) {
                return new ActionRest();
            } else {
                PathPlanning pathPlanning = moduleManager.getModule("TacticsAmbulance.PathPlanning");
                pathPlanning.setFrom(agentInfo.getPosition());
                pathPlanning.setDestination(this.target);
                List<EntityID> path = pathPlanning.calc().getResult();
                if(path != null) {
                    return new ActionMove(path);
                }
            }
        }
        return new ActionRest();
    }

    private Action getMoveAction(AgentInfo agentInfo, WorldInfo worldInfo, ModuleManager moduleManager) {
        if(worldInfo.getEntity(this.target) instanceof Area) {
            if (agentInfo.getPosition().getValue() == this.target.getValue()) {
                return new ActionRest();
            } else {
                PathPlanning pathPlanning = moduleManager.getModule("TacticsAmbulance.PathPlanning");
                pathPlanning.setFrom(agentInfo.getPosition());
                pathPlanning.setDestination(this.target);
                List<EntityID> path = pathPlanning.calc().getResult();
                if(path != null) {
                    return new ActionMove(path);
                }
            }
        }
        return null;
    }

    private Action getRescueTask(AgentInfo agentInfo, WorldInfo worldInfo, ModuleManager moduleManager) {
        StandardEntity entity = worldInfo.getEntity(this.target);
        if(entity instanceof Human) {
            Human human = (Human) entity;
            if(agentInfo.getPosition().getValue() != human.getPosition().getValue()) {
                PathPlanning pathPlanning = moduleManager.getModule("TacticsAmbulance.PathPlanning");
                pathPlanning.setFrom(agentInfo.getPosition());
                pathPlanning.setDestination(human.getPosition());
                List<EntityID> path = pathPlanning.calc().getResult();
                if(path != null) {
                    return new ActionMove(path);
                }
            } else {
                if(human.isBuriednessDefined() && human.getBuriedness() > 0) {
                    return new ActionRescue(human);
                }
            }
        }
        return null;
    }

    private Action getLoadTask(AgentInfo agentInfo, WorldInfo worldInfo, ModuleManager moduleManager) {
        StandardEntity entity = worldInfo.getEntity(this.target);
        if(entity instanceof Human) {
            Human human = (Human) entity;
            if(agentInfo.getPosition().getValue() != human.getPosition().getValue()) {
                PathPlanning pathPlanning = moduleManager.getModule("TacticsAmbulance.PathPlanning");
                pathPlanning.setFrom(agentInfo.getPosition());
                pathPlanning.setDestination(human.getPosition());
                List<EntityID> path = pathPlanning.calc().getResult();
                if(path != null) {
                    return new ActionMove(path);
                }
            } else {
                if(human.isBuriednessDefined() && human.getBuriedness() > 0) {
                    return new ActionRescue(human);
                }
                if(human.getStandardURN() == CIVILIAN) {
                    return new ActionLoad(human.getID());
                }
            }
        }
        return null;
    }

    private Action getUnloadTask(AgentInfo agentInfo, WorldInfo worldInfo, ModuleManager moduleManager) {
        if(agentInfo.someoneOnBoard() == null) {
            return null;
        }
        if(worldInfo.getEntity(this.target) instanceof Area) {
            if(agentInfo.getPosition().getValue() != this.target.getValue()) {
                PathPlanning pathPlanning = moduleManager.getModule("TacticsAmbulance.PathPlanning");
                pathPlanning.setFrom(agentInfo.getPosition());
                pathPlanning.setDestination(this.target);
                List<EntityID> path = pathPlanning.calc().getResult();
                if(path != null) {
                    return new ActionMove(path);
                }
            } else {
                return new ActionUnload();
            }
        }
        return null;
    }

    private Action getScoutAction(AgentInfo agentInfo, ModuleManager moduleManager) {
        if(this.scoutTargets == null || this.scoutTargets.isEmpty()) {
            return null;
        }
        PathPlanning pathPlanning = moduleManager.getModule("TacticsAmbulance.PathPlanning");
        pathPlanning.setFrom(agentInfo.getPosition());
        pathPlanning.setDestination(this.scoutTargets);
        List<EntityID> path = pathPlanning.calc().getResult();
        if(path != null) {
            return new ActionMove(path);
        }
        return null;
    }
}
