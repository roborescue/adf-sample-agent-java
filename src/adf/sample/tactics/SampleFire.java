package adf.sample.tactics;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.action.fire.ActionExtinguish;
import adf.agent.action.fire.ActionRefill;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.agent.communication.standard.bundle.topdown.CommandAmbulance;
import adf.agent.communication.standard.bundle.topdown.CommandFire;
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
import adf.component.module.complex.BuildingSelector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsFire;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.HYDRANT;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

public class SampleFire extends TacticsFire {
    private static final int ACTION_UNKNOWN = -1;
    private static final int ACTION_REST = CommandFire.ACTION_REST;
    private static final int ACTION_MOVE = CommandFire.ACTION_MOVE;
    private static final int ACTION_EXTINGUISH = CommandFire.ACTION_EXTINGUISH;
    private static final int ACTION_REFILL = CommandFire.ACTION_REFILL;
    private static final int ACTION_SCOUT = 5;

    private int maxWater;
    private int maxExtinguishDistance;
    private int maxExtinguishPower;

    private int task;
    private EntityID target;
    private Collection<EntityID> scoutTargets;
    private EntityID commanderID;

    private PathPlanning pathPlanning;
    private BuildingSelector buildingSelector;
    private Search search;
    private Clustering clustering;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.task = ACTION_UNKNOWN;
        this.maxWater = scenarioInfo.getFireTankMaximum();
        this.maxExtinguishDistance = scenarioInfo.getFireExtinguishMaxDistance();
        this.maxExtinguishPower = scenarioInfo.getFireExtinguishMaxSum();
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
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.search.updateInfo(messageManager);
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.buildingSelector.updateInfo(messageManager);

        this.updateTask(agentInfo, worldInfo, messageManager, true);
        if(this.task != ACTION_UNKNOWN) {
            return this.getTaskAction(agentInfo, worldInfo, moduleManager);
        }

        FireBrigade agent = (FireBrigade) agentInfo.me();
        EntityID target = this.buildingSelector.calc().getTarget();
        if(target != null) {
            Action action = moduleManager
                    .getExtAction("TacticsFire.ActionFireFighting")
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
                    .getExtAction("TacticsFire.ActionExtMove")
                    .setTarget(this.search.calc().getTarget())
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
                    new MessageFireBrigade(true, agent, MessageFireBrigade.ACTION_REST,  agent.getPosition())
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
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandFire.class)) {
            CommandFire command = (CommandFire) message;
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
        } else if(this.task == ACTION_EXTINGUISH) {
            return this.checkExtinguishTask(worldInfo);
        } else if(this.task == ACTION_REFILL) {
            return this.checkRefillTask(agentInfo);
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

    private boolean checkScoutTask(WorldInfo worldInfo) {
        this.scoutTargets.removeAll(worldInfo.getChanged().getChangedEntities());
        return (this.scoutTargets == null || this.scoutTargets.isEmpty());
    }

    private boolean checkExtinguishTask(WorldInfo worldInfo) {
        if(this.target == null) {
            return true;
        }
        StandardEntity entity = worldInfo.getEntity(this.target);
        if(entity instanceof Building) {
            return (((Building)entity).getFieryness() >= 4);
        }
        return true;
    }

    private boolean checkRefillTask(AgentInfo agentInfo) {
        return (((FireBrigade)agentInfo.me()).getWater() == this.maxWater);
    }

    private Action getTaskAction(AgentInfo agentInfo, WorldInfo worldInfo, ModuleManager moduleManager) {
        if(this.task == ACTION_REST) {
            return this.getRestAction(agentInfo, worldInfo, moduleManager);
        } else if(this.task == ACTION_MOVE) {
            return this.getMoveAction(agentInfo, worldInfo, moduleManager);
        } else if(this.task == ACTION_EXTINGUISH) {
            return this.getExtinguishTask(agentInfo, worldInfo, moduleManager);
        } else if(this.task == ACTION_REFILL) {
            return this.getRefillAction(agentInfo, worldInfo, moduleManager);
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

    private Action getRefillAction(AgentInfo agentInfo, WorldInfo worldInfo, ModuleManager moduleManager) {
        Collection<EntityID> refillAreaIDs = worldInfo.getEntityIDsOfType(REFUGE);
        if(refillAreaIDs.contains(agentInfo.getPosition())) {
            return new ActionRefill();
        }
        PathPlanning pathPlanning = moduleManager.getModule("TacticsAmbulance.PathPlanning");
        if(this.target != null) {
            if (agentInfo.getPosition().getValue() == this.target.getValue()) {
                return new ActionRefill();
            }
            pathPlanning.setFrom(agentInfo.getPosition());
            pathPlanning.setDestination(this.target);
            List<EntityID> path = pathPlanning.calc().getResult();
            if (path != null) {
                return new ActionMove(path);
            }
        } else {
            pathPlanning.setFrom(agentInfo.getPosition());
            pathPlanning.setDestination(refillAreaIDs);
            List<EntityID> path = pathPlanning.calc().getResult();
            if (path != null) {
                return new ActionMove(path);
            }
            refillAreaIDs = worldInfo.getEntityIDsOfType(HYDRANT);
            if(refillAreaIDs.contains(agentInfo.getPosition())) {
                return new ActionRefill();
            }
            pathPlanning.setFrom(agentInfo.getPosition());
            pathPlanning.setDestination(refillAreaIDs);
            path = pathPlanning.calc().getResult();
            if (path != null) {
                return new ActionMove(path);
            }
        }
        return null;
    }

    private Action getExtinguishTask(AgentInfo agentInfo, WorldInfo worldInfo, ModuleManager moduleManager) {
        if(this.target == null) {
            return null;
        }
        StandardEntity entity = worldInfo.getEntity(this.target);
        if(entity instanceof Building) {
            Human agent = (Human)agentInfo.me();
            if (worldInfo.getDistance(agent, entity) < this.maxExtinguishDistance) {
                new ActionExtinguish((Building) entity, this.maxExtinguishPower);
            }
            PathPlanning pathPlanning = moduleManager.getModule("TacticsAmbulance.PathPlanning");
            pathPlanning.setFrom(agent.getPosition());
            pathPlanning.setDestination(this.target);
            List<EntityID> path = pathPlanning.calc().getResult();
            if (path != null) {
                return new ActionMove(path);
            }
        }
        return null;
    }
}
