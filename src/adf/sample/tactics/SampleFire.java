package adf.sample.tactics;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.action.fire.ActionExtinguish;
import adf.agent.action.fire.ActionRefill;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
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

    private int commandType;
    private EntityID target;
    private Collection<EntityID> scoutTargets;
    private EntityID commanderID;

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
        // init value
        this.maxWater = scenarioInfo.getFireTankMaximum();
        this.maxExtinguishDistance = scenarioInfo.getFireExtinguishMaxDistance();
        this.maxExtinguishPower = scenarioInfo.getFireExtinguishMaxSum();
        this.commandType = ACTION_UNKNOWN;
        //init ExtAction
        moduleManager.getExtAction("TacticsFire.ActionFireFighting", "adf.sample.extaction.ActionFireFighting");
        moduleManager.getExtAction("TacticsFire.ActionExtMove", "adf.sample.extaction.ActionExtMove");
        // init Algorithm Module
        switch  (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("TacticsFire.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.clustering = moduleManager.getModule("TacticsFire.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                this.search = moduleManager.getModule("TacticsFire.Search", "adf.sample.module.complex.SampleSearch");
                this.buildingSelector = moduleManager.getModule("TacticsFire.BuildingSelector", "adf.sample.module.complex.SampleBuildingSelector");
                break;
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("TacticsFire.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.clustering = moduleManager.getModule("TacticsFire.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                this.search = moduleManager.getModule("TacticsFire.Search", "adf.sample.module.complex.SampleSearch");
                this.buildingSelector = moduleManager.getModule("TacticsFire.BuildingSelector", "adf.sample.module.complex.SampleBuildingSelector");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("TacticsFire.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.clustering = moduleManager.getModule("TacticsFire.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                this.search = moduleManager.getModule("TacticsFire.Search", "adf.sample.module.complex.SampleSearch");
                this.buildingSelector = moduleManager.getModule("TacticsFire.BuildingSelector", "adf.sample.module.complex.SampleBuildingSelector");
                break;
        }
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning.precompute(precomputeData);
        this.clustering.precompute(precomputeData);
        this.search.precompute(precomputeData);
        this.buildingSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning.resume(precomputeData);
        this.clustering.resume(precomputeData);
        this.search.resume(precomputeData);
        this.buildingSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        this.pathPlanning.preparate();
        this.clustering.preparate();
        this.search.preparate();
        this.buildingSelector.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.search.updateInfo(messageManager);
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.buildingSelector.updateInfo(messageManager);

        FireBrigade agent = (FireBrigade) agentInfo.me();
        // command
        Action action = this.getCommandAction(agentInfo, worldInfo, messageManager);
        if(action != null) {
            this.sendActionMessage(messageManager, agent, action);
            return action;
        }
        // autonomous
        EntityID target = this.buildingSelector.calc().getTarget();
        if(target != null) {
            action = moduleManager
                    .getExtAction("TacticsFire.ActionFireFighting")
                    .setTarget(target)
                    .calc().getAction();
            if(action != null) {
                this.sendActionMessage(messageManager, agent, action);
                return action;
            }
        }
        target = this.search.calc().getTarget();
        if(target != null) {
            action = moduleManager
                    .getExtAction("TacticsFire.ActionExtMove")
                    .setTarget(target)
                    .calc().getAction();
            if(action != null) {
                this.sendActionMessage(messageManager, agent, action);
                return action;
            }
        }

        messageManager.addMessage(
                new MessageFireBrigade(true, agent, MessageFireBrigade.ACTION_REST,  agent.getPosition())
        );
        return new ActionRest();
    }

    private Action getCommandAction(AgentInfo agentInfo, WorldInfo worldInfo, MessageManager messageManager) {
        if(this.isCommandComplete(agentInfo, worldInfo)) {
            if(this.commandType != ACTION_UNKNOWN) {
                messageManager.addMessage(new MessageReport(true, true, false, this.commanderID));
            }
            this.commandType = ACTION_UNKNOWN;
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
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandFire.class)) {
            CommandFire command = (CommandFire) message;
            if(command.getToID().getValue() == agentID.getValue()) {
                this.commandType = command.getAction();
                this.target = command.getTargetID();
                this.commanderID = command.getSenderID();
                break;
            }
        }
        if(this.commandType == ACTION_REST) {
            return this.getRestAction(agentInfo, worldInfo);
        } else if(this.commandType == ACTION_MOVE) {
            EntityID position = agentInfo.getPosition();
            if (position.getValue() == this.target.getValue()) {
                return new ActionRest();
            } else {
                List<EntityID> path = this.pathPlanning.getResult(position, this.target);
                if(path != null) {
                    return new ActionMove(path);
                }
            }
        } else if(this.commandType == ACTION_EXTINGUISH) {
            return this.getExtinguishAction(agentInfo, worldInfo);
        } else if(this.commandType == ACTION_REFILL) {
            return this.getRefillAction(agentInfo, worldInfo);
        } else if(this.commandType == ACTION_SCOUT) {
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

    private boolean isCommandComplete(AgentInfo agentInfo, WorldInfo worldInfo) {
        if (this.commandType == ACTION_REST) {
            if (worldInfo.getEntity(this.target).getStandardURN() == REFUGE) {
                AmbulanceTeam agent = (AmbulanceTeam) agentInfo.me();
                if (agent.getPosition().getValue() == this.target.getValue()) {
                    return (agent.getDamage() == 0);
                }
            }
            return false;
        } else if (this.commandType == ACTION_MOVE) {
            return (agentInfo.getPosition().getValue() == this.target.getValue());
        } else if (this.commandType == ACTION_EXTINGUISH) {
            return (((Building) worldInfo.getEntity(this.target)).getFieryness() >= 4);
        } else if (this.commandType == ACTION_REFILL) {
            return (((FireBrigade)agentInfo.me()).getWater() == this.maxWater);
        } else if (this.commandType == ACTION_SCOUT) {
            this.scoutTargets.removeAll(worldInfo.getChanged().getChangedEntities());
            return (this.scoutTargets == null || this.scoutTargets.isEmpty());
        }
        return true;
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

    private Action getRefillAction(AgentInfo agentInfo, WorldInfo worldInfo) {
        EntityID position = agentInfo.getPosition();
        Collection<EntityID> refillAreaIDs = worldInfo.getEntityIDsOfType(REFUGE);
        if(refillAreaIDs.contains(position)) {
            return new ActionRefill();
        }
        if(this.target != null) {
            if (position.getValue() == this.target.getValue()) {
                return new ActionRefill();
            }
            this.pathPlanning.setFrom(position);
            this.pathPlanning.setDestination(this.target);
            List<EntityID> path = this.pathPlanning.calc().getResult();
            if (path != null) {
                return new ActionMove(path);
            }
        } else {
            this.pathPlanning.setFrom(position);
            this.pathPlanning.setDestination(refillAreaIDs);
            List<EntityID> path = this.pathPlanning.calc().getResult();
            if (path != null) {
                return new ActionMove(path);
            }
            refillAreaIDs = worldInfo.getEntityIDsOfType(HYDRANT);
            if(refillAreaIDs.contains(agentInfo.getPosition())) {
                return new ActionRefill();
            }
            this.pathPlanning.setFrom(position);
            this.pathPlanning.setDestination(refillAreaIDs);
            path = this.pathPlanning.calc().getResult();
            if (path != null) {
                return new ActionMove(path);
            }
        }
        return null;
    }

    private Action getExtinguishAction(AgentInfo agentInfo, WorldInfo worldInfo) {
        if(this.target != null) {
            StandardEntity entity = worldInfo.getEntity(this.target);
            if (entity instanceof Building) {
                Human agent = (Human) agentInfo.me();
                if (worldInfo.getDistance(agent, entity) < this.maxExtinguishDistance) {
                    new ActionExtinguish((Building) entity, this.maxExtinguishPower);
                }
                this.pathPlanning.setFrom(agent.getPosition());
                this.pathPlanning.setDestination(this.target);
                List<EntityID> path = this.pathPlanning.calc().getResult();
                if (path != null) {
                    return new ActionMove(path);
                }
            }
        }
        return null;
    }

    private void sendActionMessage(MessageManager messageManager, FireBrigade fireBrigade, Action action) {
        Class<? extends Action> actionClass = action.getClass();
        int actionIndex = -1;
        EntityID target = null;
        if (actionClass == ActionMove.class) {
            List<EntityID> path = ((ActionMove) action).getPath();
            actionIndex = MessageFireBrigade.ACTION_MOVE;
            if(path.size() > 0) {
                target = path.get(path.size() - 1);
            }
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
            messageManager.addMessage(new MessageFireBrigade(true, fireBrigade, actionIndex, target));
        }
    }
}
