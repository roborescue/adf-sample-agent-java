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
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
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

    private int task;
    private EntityID target;
    private Collection<EntityID> scoutTargets;
    private EntityID commanderID;

    private PathPlanning pathPlanning;
    private RoadSelector roadSelector;
    private Search search;
    private Clustering clustering;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.task = ACTION_UNKNOWN;
        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.BLOCKADE
        );
        this.clearDistance = scenarioInfo.getClearRepairDistance();
        //init ExtAction
        moduleManager.getExtAction("TacticsPolice.ActionExtClear", "adf.sample.extaction.ActionExtClear");
        moduleManager.getExtAction("TacticsPolice.ActionExtMove", "adf.sample.extaction.ActionExtMove");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule("TacticsPolice.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule("TacticsPolice.Clustering", "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.precompute(precomputeData);
        this.search = moduleManager.getModule("TacticsPolice.Search", "adf.sample.module.complex.SampleSearch");
        this.search.precompute(precomputeData);
        this.roadSelector = moduleManager.getModule("TacticsPolice.RoadSelector", "adf.sample.module.complex.SampleRoadSelector");
        this.roadSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule("TacticsPolice.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule("TacticsPolice.Clustering", "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.resume(precomputeData);
        this.search = moduleManager.getModule("TacticsPolice.Search", "adf.sample.module.complex.SampleSearch");
        this.search.resume(precomputeData);
        this.roadSelector = moduleManager.getModule("TacticsPolice.RoadSelector", "adf.sample.module.complex.SampleRoadSelector");
        this.roadSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule("TacticsPolice.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.preparate();
        this.clustering = moduleManager.getModule("TacticsPolice.Clustering", "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.preparate();
        this.search = moduleManager.getModule("TacticsPolice.Search", "adf.sample.module.complex.SampleSearch");
        this.search.preparate();
        this.roadSelector = moduleManager.getModule("TacticsPolice.RoadSelector", "adf.sample.module.complex.SampleRoadSelector");
        this.roadSelector.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.search.updateInfo(messageManager);
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.roadSelector.updateInfo(messageManager);

        this.updateTask(agentInfo, worldInfo, messageManager, true);
        if(this.task != ACTION_UNKNOWN) {
            return this.getTaskAction(agentInfo, worldInfo, moduleManager);
        }

        PoliceForce agent = (PoliceForce) agentInfo.me();
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

        //check buriedness
        if(agent.getBuriedness() > 0) {
            messageManager.addMessage(
                    new MessagePoliceForce(true, agent, MessagePoliceForce.ACTION_REST, agent.getPosition())
            );
        }
        return new ActionRest();
    }

    private CommunicationMessage getActionMessage(WorldInfo worldInfo, PoliceForce policeForce, Action action) {
        Class<? extends Action> actionClass = action.getClass();
        int actionIndex = -1;
        EntityID target = null;
        if(actionClass == ActionMove.class) {
            List<EntityID> path = ((ActionMove)action).getPath();
            actionIndex = MessagePoliceForce.ACTION_MOVE;
            target = path.get(path.size() - 1);
        } else if(actionClass == ActionClear.class) {
            actionIndex = MessagePoliceForce.ACTION_CLEAR;
            ActionClear ac = (ActionClear)action;
            target = ac.getTarget();
            if(target == null) {
                for(StandardEntity entity : worldInfo.getObjectsInRange(ac.getPosX(), ac.getPosY(), this.clearDistance)) {
                    if(entity.getStandardURN() == StandardEntityURN.BLOCKADE) {
                        if(this.intersect(
                                policeForce.getX(), policeForce.getY(),
                                ac.getPosX(), ac.getPosY(),
                                (Blockade)entity
                        )) {
                            target = entity.getID();
                            break;
                        }
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

    private boolean intersect(double agentX, double agentY, double pointX, double pointY, Blockade blockade) {
        List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(blockade.getApexes()), true);
        for(Line2D line : lines) {
            Point2D start = line.getOrigin();
            Point2D end = line.getEndPoint();
            double startX = start.getX();
            double startY = start.getY();
            double endX = end.getX();
            double endY = end.getY();
            if(java.awt.geom.Line2D.linesIntersect(
                    agentX, agentY, pointX, pointY,
                    startX, startY, endX, endY
            )) {
                double midX = (startX + endX) / 2;
                double midY = (startY + endY) / 2;
                if(!equalsPoint(pointX, pointY, midX, midY) && !equalsPoint(agentX, agentY, midX, midY)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean equalsPoint(double p1X, double p1Y, double p2X, double p2Y) {
        return this.equalsPoint(p1X, p1Y, p2X, p2Y, 1.0D);
    }

    private boolean equalsPoint(double p1X, double p1Y, double p2X, double p2Y, double range) {
        return (p2X - range < p1X && p1X < p2X + range) && (p2Y - range < p1Y && p1Y < p2Y + range);
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
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandPolice.class)) {
            CommandPolice command = (CommandPolice) message;
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
        } else if(this.task == ACTION_CLEAR) {
            return this.checkClearTask(agentInfo, worldInfo);
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
        if(this.task == ACTION_REST) {
            return this.getRestAction(agentInfo, worldInfo, moduleManager);
        } else if(this.task == ACTION_MOVE) {
            return this.getMoveAction(agentInfo, worldInfo, moduleManager);
        } else if(this.task == ACTION_CLEAR) {
            return this.getClearTask(moduleManager);
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

    private Action getClearTask(ModuleManager moduleManager) {
        return moduleManager
                .getExtAction("TacticsPolice.ActionExtClear")
                .setTarget(this.target)
                .calc().getAction();
    }
}
