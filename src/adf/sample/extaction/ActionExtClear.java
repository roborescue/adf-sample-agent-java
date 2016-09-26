package adf.sample.extaction;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.police.ActionClear;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import com.google.common.collect.Lists;
import jdk.nashorn.internal.ir.Block;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;
import java.util.stream.Collectors;

public class ActionExtClear extends ExtAction {
    private int clearDistance;

    private EntityID target;
    private Map<EntityID, Set<Edge>> edgeCache;
    private Map<EntityID, Set<Point2D>> movePointCache;

    public ActionExtClear(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.clearDistance = this.scenarioInfo.getClearRepairDistance();
        this.target = null;
        this.edgeCache = new HashMap<>();
        this.movePointCache = new HashMap<>();
    }

    @Override
    public ExtAction setTarget(EntityID... targets) {
        this.target = null;
        if(targets == null) { return this; }

        for(EntityID entityID : targets) {
            StandardEntity entity = this.worldInfo.getEntity(entityID);
            if(entity instanceof Road) {
                this.target = entityID;
                return this;
            } else if(entity.getStandardURN().equals(StandardEntityURN.BLOCKADE)) {
                this.target = ((Blockade)entity).getPosition();
                return this;
            } else if(entity instanceof Building) {
                this.target = entityID;
                return this;
            }
        }
        return this;
    }

    @Override
    public ExtAction calc() {
        this.result = null;
        if(this.target == null) {
            return this;
        }
        PoliceForce policeForce = (PoliceForce)this.agentInfo.me();
        EntityID agentPosition = policeForce.getPosition();
        StandardEntity targetEntity = this.worldInfo.getEntity(this.target);
        StandardEntity positionEntity = this.worldInfo.getEntity(agentPosition);
        if(!(targetEntity instanceof Area)) {
            return this;
        }

        if (positionEntity instanceof Road) {
            this.result = this.getRescueAction(policeForce, (Road) positionEntity);
            if (this.result != null) {
                return this;
            }
        }

        if(agentPosition.equals(this.target)) {
            if(targetEntity instanceof Road) {
                this.result = this.calcTargetPosition(policeForce, (Road)targetEntity);
            }
            return this;
        } else if(((Area)targetEntity).getEdgeTo(agentPosition) != null) {
            if(targetEntity instanceof Road) {
                this.result = this.calcNeighbourPosition(policeForce, (Road) targetEntity);
                if (this.result != null) {
                    return this;
                }
            } else {

            }
        } else {
            //neighbour move clear
            PathPlanning pathPlanning = this.moduleManager.getModule("TacticsPolice.PathPlanning");
            List<EntityID> path = pathPlanning.getResult(agentPosition, this.target);
            if (path != null && path.size() > 0) {
                EntityID id = path.get(0);
                if(id.getValue() == agentPosition.getValue()) {
                    if(path.size() > 1) {
                        StandardEntity entity = this.worldInfo.getEntity(path.get(1));
                        if (entity instanceof Road) {
                            this.result = this.calcNeighbourPosition(policeForce, (Road) entity);
                            if (this.result != null) {
                                return this;
                            }
                        }
                    }
                }else {
                    int index = path.indexOf(agentPosition);
                    StandardEntity entity = this.worldInfo.getEntity(path.get(index + 1));
                    if (entity instanceof Road) {
                        Road road = (Road) entity;
                        if (road.isBlockadesDefined() && road.getBlockades().size() > 0) {
                            this.result = this.calcNeighbourPosition(policeForce, road);
                            if (this.result != null) return this;
                        }
                    }
                }
            }
            //clear
            this.result = this.calcOtherPosition(policeForce, (Road)targetEntity);
            if(this.result != null) {
                return this;
            }
        }
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private Action getRescueAction(PoliceForce police, Road road) {
        if(!road.isBlockadesDefined()) {
            return null;
        }
        Collection<Blockade> blockades = this.worldInfo.getBlockades(road)
                .stream()
                .filter(Blockade::isApexesDefined)
                .collect(Collectors.toSet());

        Collection<StandardEntity> agents = this.worldInfo.getEntitiesOfType(
                StandardEntityURN.AMBULANCE_TEAM,
                StandardEntityURN.FIRE_BRIGADE
        );

        double policeX = police.getX();
        double policeY = police.getY();
        double minDistance = Double.MAX_VALUE;
        Action moveAction = null;
        for(StandardEntity entity : agents) {
            Human human = (Human)entity;
            if(!human.isPositionDefined() ||  human.getPosition().getValue() != road.getID().getValue()) {
                continue;
            }
            double humanX = human.getX();
            double humanY = human.getY();
            ActionClear actionClear = null;
            for(Blockade blockade : blockades) {
                if(!this.isInside(humanX, humanY, blockade.getApexes())) {
                    continue;
                }
                double distance = this.getDistance(policeX, policeY, humanX, humanY);
                if(this.intersect(policeX, policeY, humanX, humanY, road)) {
                    Set<Point2D> points = this.getMovePoints(road);
                    Action action = this.calcIntersectEdgeAction(policeX, policeY, humanX, humanY, road, points, blockades);
                    if(action == null) {
                        continue;
                    }
                    if(action.getClass() == ActionClear.class) {
                        if(actionClear != null) {
                            if(actionClear.getTarget() != null) {
                                Blockade another = (Blockade)this.worldInfo.getEntity(actionClear.getTarget());
                                if(this.intersect(blockade, another)) {
                                    return new ActionClear(another);
                                }
                                int anotherDistance = this.worldInfo.getDistance(police, another);
                                int blockadeDistance = this.worldInfo.getDistance(police, blockade);
                                if(anotherDistance > blockadeDistance) {
                                    return action;
                                }
                            }
                            return actionClear;
                        } else {
                            actionClear = (ActionClear) action;
                        }
                    } else if(action.getClass() == ActionMove.class && distance < minDistance) {
                        minDistance = distance;
                        moveAction = action;
                    }
                }else if(this.intersect(policeX, policeY, humanX, humanY, blockade)) {
                    Vector2D vector = this.scaleClear(this.getVector(policeX, policeY, humanX, humanY));
                    if (this.intersect(policeX, policeY, (policeX + vector.getX()), (policeY + vector.getY()), blockade)) {
                        if(actionClear == null) {
                            actionClear = new ActionClear((int) (policeX + vector.getX()), (int) (policeY + vector.getY()), blockade);
                        } else {
                            if(actionClear.getTarget() != null) {
                                Blockade another = (Blockade)this.worldInfo.getEntity(actionClear.getTarget());
                                if(this.intersect(blockade, another)) {
                                    return new ActionClear(another);
                                }
                                int distance1 = this.worldInfo.getDistance(police, another);
                                int distance2 = this.worldInfo.getDistance(police, blockade);
                                if(distance1 > distance2) {
                                    return new ActionClear((int) (policeX + vector.getX()), (int) (policeY + vector.getY()), blockade);
                                }
                            }
                            return actionClear;
                        }
                    } else if (distance < minDistance) {
                        minDistance = distance;
                        moveAction = new ActionMove(Lists.newArrayList(road.getID()), (int) humanX, (int) humanY);
                    }
                }
            }
            if(actionClear != null) {
                return actionClear;
            }
        }
        return moveAction;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private Action calcTargetPosition(StandardEntity targetEntity) {
        if(targetEntity instanceof Building) {
            return null;
        }
        Road road = (Road)targetEntity;
        if(!road.isBlockadesDefined() || road.getBlockades().isEmpty()) {
            return null;
        }
        Human agent = (Human)this.agentInfo.me();
        Collection<Blockade> blockades = this.worldInfo.getBlockades(road);
        int minDistance = Integer.MAX_VALUE;
        Blockade clearBlockade = null;
        for(Blockade blockade : blockades) {
            for(Blockade another : blockades) {
                if(!blockade.getID().equals(another.getID()) && this.intersect(blockade, another)) {
                    int distance1 = this.worldInfo.getDistance(agent, blockade);
                    int distance2 = this.worldInfo.getDistance(agent, another);
                    if(distance1 <= distance2 && distance1 < minDistance) {
                        minDistance = distance1;
                        clearBlockade = blockade;
                    } else if(distance2 < minDistance) {
                        minDistance = distance2;
                        clearBlockade = another;
                    }
                }
            }
        }
        if(clearBlockade != null) {
            if(minDistance < this.clearDistance) {
                return new ActionClear(clearBlockade);
            } else {
                return new ActionMove(
                        Lists.newArrayList(agent.getPosition()),
                        clearBlockade.getX(),
                        clearBlockade.getY()
                );
            }
        }
        double agentX = agent.getX();
        double agentY = agent.getY();
        clearBlockade = null;
        Double minPointDistance = Double.MAX_VALUE;
        int clearX = 0;
        int clearY = 0;
        for(Blockade blockade : blockades) {
            if(blockade.isApexesDefined()) {
                int[] apexes = blockade.getApexes();
                for (int i = 0; i < (apexes.length - 2); i += 2) {
                    double distance = this.getDistance(agentX, agentY, apexes[i], apexes[i + 1]);
                    if(distance < minPointDistance) {
                        clearBlockade = blockade;
                        minPointDistance = distance;
                        clearX = apexes[i];
                        clearY = apexes[i + 1];
                    }
                }
            }
        }
        if(clearBlockade != null) {
            if(minDistance < this.clearDistance) {
                Vector2D vector = this.scaleClear(this.getVector(agentX, agentY, clearX, clearY));
                clearX = (int)(agentX + vector.getX());
                clearY = (int)(agentY + vector.getY());
                return new ActionClear(clearX, clearY, clearBlockade);
            }
            return new ActionMove(Lists.newArrayList(agent.getPosition()), clearX, clearY);
        }
        return null;
    }

    private Action calcTargetPosition(PoliceForce policeForce, Road targetRoad) {
        if(!targetRoad.isBlockadesDefined() || targetRoad.getBlockades().isEmpty()) {
            return null;
        }
        Set<Edge> checkEdges = this.edgeCache.get(targetRoad.getID());
        if(checkEdges == null) {
            checkEdges = new HashSet<>();
            for(Edge edge : targetRoad.getEdges()) {
                if (edge.isPassable()) {
                    checkEdges.add(edge);
                }
            }
        }
        double agentX = policeForce.getX();
        double agentY = policeForce.getY();
        Collection<Blockade> blockades = this.worldInfo.getBlockades(targetRoad);
        List<Edge> intersectEdges = new ArrayList<>();
        Set<Edge> removeEdges = new HashSet<>();
        for(Edge edge : checkEdges) {
            double midX = (edge.getStartX() + edge.getEndX()) / 2;
            double midY = (edge.getStartY() + edge.getEndY()) / 2;
            if (this.intersect(agentX, agentY, midX, midY, targetRoad)) {
                intersectEdges.add(edge);
            } else {
                Vector2D vector = this.scaleClear(this.getVector(agentX, agentY, midX, midY));
                double clearX = agentX + vector.getX();
                double clearY = agentY + vector.getY();
                Action action = null;
                for(Blockade blockade : blockades) {
                    if(this.intersect(agentX, agentY, clearX, clearY, blockade)) {
                        if(action == null) {
                            action = new ActionClear((int) clearX, (int) clearY, blockade);
                        } else {
                            if(action instanceof ActionClear) {
                                EntityID another = ((ActionClear)action).getTarget();
                                if(this.intersect(blockade, (Blockade)this.worldInfo.getEntity(another))) {
                                    return new ActionClear(another);
                                }
                            } else {
                                action = new ActionClear((int) clearX, (int) clearY, blockade);
                            }
                        }
                    }
                    if(this.intersect(agentX, agentY, midX, midY, blockade)) {
                        if(action == null) {
                            action = new ActionMove(Lists.newArrayList(targetRoad.getID()), (int) midX, (int) midY);
                        }
                    }
                }
                if(action != null) {
                    checkEdges.removeAll(removeEdges);
                    this.edgeCache.put(targetRoad.getID(), checkEdges);
                    return action;
                }
                removeEdges.add(edge);
            }
        }
        checkEdges.removeAll(removeEdges);
        this.edgeCache.put(targetRoad.getID(), checkEdges);

        Set<Point2D> points = this.getMovePoints(targetRoad);
        for(Edge edge : intersectEdges) {
            Action action = this.calcIntersectEdgeAction(agentX, agentY, edge, targetRoad, points, blockades);
            if(action != null) return action;
        }
        double minDistance = Double.MAX_VALUE;
        Blockade targetBlockade = null;
        double x = Double.MAX_VALUE;
        double y = Double.MAX_VALUE;
        for(Blockade blockade : blockades) {
            double distance = this.getDistance(agentX, agentY, blockade.getX(), blockade.getY());
            if (distance < minDistance) {
                minDistance = distance;
                targetBlockade = blockade;
                x = blockade.getX();
                y = blockade.getY();
            }
        }
        if(minDistance < this.clearDistance) {
            Vector2D vector = this.scaleClear(this.getVector(agentX, agentY, x, y));
            if(targetBlockade != null) {
                return new ActionClear((int) (agentX + vector.getX()), (int) (agentY + vector.getY()), targetBlockade);
            }
            return new ActionClear((int) (agentX + vector.getX()), (int) (agentY + vector.getY()));
        }
        if(minDistance < Double.MAX_VALUE) {
            return new ActionMove(Lists.newArrayList(targetRoad.getID()), (int)x, (int)y);
        }
        return null;
    }

    private Action calcNeighbourPosition(PoliceForce policeForce, Road targetRoad) {
        StandardEntity agentPosition = this.worldInfo.getPosition(policeForce);
        Edge edge = targetRoad.getEdgeTo(policeForce.getPosition());
        double agentX = policeForce.getX();
        double agentY = policeForce.getY();
        double midX = (edge.getStartX() + edge.getEndX()) / 2;
        double midY = (edge.getStartY() + edge.getEndY()) / 2;
        if(agentPosition instanceof Building) {
            Vector2D vector = this.getVector(agentX, agentY, midX, midY);
            double clearX = agentX + vector.getX();
            double clearY = agentY + vector.getY();
            if(this.intersect(agentX, agentY, clearX, clearY, (Area)agentPosition)) {
                return new ActionMove(Lists.newArrayList(agentPosition.getID(), targetRoad.getID()));
            }
            double distance = this.getDistance(agentX, agentY, midX, midY);
            if(distance < this.clearDistance && targetRoad.isBlockadesDefined()) {
                vector = this.scaleClear(vector);
                clearX = agentX + vector.getX();
                clearY = agentY + vector.getY();
                for (Blockade blockade : this.worldInfo.getBlockades(targetRoad)) {
                    if (this.intersect(agentX, agentY, clearX, clearY, blockade)) {
                        return new ActionClear((int) clearX, (int) clearY, blockade);
                    }
                }
            }
            return new ActionMove(Lists.newArrayList(agentPosition.getID(), targetRoad.getID()));
        }
        if(agentPosition instanceof Road) {
            Road positionRoad = (Road)agentPosition;
            if(this.intersect(agentX, agentY, midX, midY, positionRoad)) {
                Collection<Blockade> blockades = this.worldInfo.getBlockades(positionRoad);
                Set<Point2D> points = this.movePointCache.get(targetRoad.getID());
                if (points == null) {
                    points = this.getMovePoints(targetRoad);
                    this.movePointCache.put(targetRoad.getID(), points);
                }
                Action action = this.calcIntersectEdgeAction(agentX, agentY, edge, positionRoad, points, blockades);
                if (action != null) return action;
            } else {
                double distance = this.getDistance(agentX, agentY, midX, midY);
                if (distance < this.clearDistance) {
                    Vector2D vector = this.scaleClear(this.getVector(agentX, agentY, midX, midY));
                    double clearX = agentX + vector.getX();
                    double clearY = agentY + vector.getY();
                    Action action = null;
                    for (Blockade blockade : this.worldInfo.getBlockades(positionRoad)) {
                        if(this.intersect(agentX, agentY, clearX, clearY, blockade)) {
                            if(action == null) {
                                action = new ActionClear((int) clearX, (int) clearY, blockade);
                            } else {
                                if(action instanceof ActionClear) {
                                    EntityID another = ((ActionClear)action).getTarget();
                                    if(this.intersect(blockade, (Blockade)this.worldInfo.getEntity(another))) {
                                        return new ActionClear(another);
                                    }
                                } else {
                                    action = new ActionClear((int) clearX, (int) clearY, blockade);
                                }
                            }
                        }
                        if(this.intersect(agentX, agentY, midX, midY, blockade)) {
                            if(action == null) {
                                action = new ActionMove(Lists.newArrayList(targetRoad.getID()), (int) midX, (int) midY);
                            }
                        }
                    }
                    if(action != null) {
                        return action;
                    }
                    for (Blockade blockade : this.worldInfo.getBlockades(targetRoad)) {
                        if (this.intersect(agentX, agentY, clearX, clearY, blockade)) {
                            return new ActionClear((int) clearX, (int) clearY, blockade);
                        }
                    }
                }
            }
            return new ActionMove(Lists.newArrayList(agentPosition.getID(), targetRoad.getID()));
        }
        return null;
    }

    private Action calcOtherPosition(PoliceForce policeForce, Road targetRoad) {
        double agentX = policeForce.getX();
        double agentY = policeForce.getY();
        if(!targetRoad.isBlockadesDefined()) {
            PathPlanning pathPlanning = this.moduleManager.getModule("TacticsPolice.PathPlanning");
            pathPlanning.setFrom(policeForce.getPosition()).setDestination(targetRoad.getID());
            List<EntityID> path = pathPlanning.calc().getResult();
            if (path != null && path.size() > 0) {
                return new ActionMove(path);
            }
            return null;
        }
        double minDistance = Double.MAX_VALUE;
        Blockade targetBlockade = null;
        double nearX = Double.MAX_VALUE;
        double nearY = Double.MAX_VALUE;
        for (Blockade blockade : this.worldInfo.getBlockades(targetRoad)) {
            if (blockade.isApexesDefined()) {
                int[] apex = blockade.getApexes();
                for(int i = 0; i < apex.length; i += 2) {
                    double distance = this.getDistance(agentX, agentY, apex[i], apex[i + 1]);
                    if (distance < minDistance) {
                        minDistance = distance;
                        targetBlockade = blockade;
                        nearX = apex[i];
                        nearY = apex[i + 1];
                    }
                }
            } else if (blockade.isPositionDefined()) {
                double distance = this.getDistance(agentX, agentY, blockade.getX(), blockade.getY());
                if (distance < minDistance) {
                    minDistance = distance;
                    targetBlockade = blockade;
                    nearX = blockade.getX();
                    nearY = blockade.getY();
                }
            }
        }
        if (minDistance < this.clearDistance) {
            Vector2D vector = this.scaleClear(this.getVector(agentX, agentY, nearX, nearY));
            if(targetBlockade != null) {
                return new ActionClear((int) (agentX + vector.getX()), (int) (agentY + vector.getY()), targetBlockade);
            }
            return new ActionClear((int) (agentX + vector.getX()), (int) (agentY + vector.getY()));
        }
        return null;
    }

    private Action calcIntersectEdgeAction(double agentX, double agentY, Edge edge, Road road, Set<Point2D> movePoints, Collection<Blockade> blockades) {
        double midX = (edge.getStartX() + edge.getEndX()) / 2;
        double midY = (edge.getStartY() + edge.getEndY()) / 2;
        return this.calcIntersectEdgeAction(agentX, agentY, midX, midY, road, movePoints, blockades);
    }

    private Action calcIntersectEdgeAction(double agentX, double agentY, double pointX, double pointY, Road road, Set<Point2D> movePoints, Collection<Blockade> blockades) {
        Point2D bestPoint = null;
        Point2D subPoint = null;
        double bastDistance  = Double.MAX_VALUE;
        double edgeMinDistance  = Double.MAX_VALUE;
        double agentMaxDistance = Double.MIN_VALUE;
        for(Point2D p : movePoints) {
            if(!this.intersect(agentX, agentY, p.getX(), p.getY(), road)) {
                double distance = this.getDistance(pointX, pointY, p.getX(), p.getY());
                if(distance < bastDistance) {
                    if(!this.intersect(pointX, pointY, p.getX(), p.getY(), road)) {
                        bestPoint = p;
                        bastDistance = distance;
                    }
                }
                if(distance < edgeMinDistance) {
                    double agentDistance = this.getDistance(agentX, agentY, p.getX(), p.getY());
                    if(agentDistance > agentMaxDistance) {
                        subPoint = p;
                        edgeMinDistance = distance;
                        agentMaxDistance = agentDistance;
                    }
                }
            }
        }
        if(bestPoint == null) bestPoint = subPoint;
        if(bestPoint != null) {
            double pX = bestPoint.getX();
            double pY = bestPoint.getY();
            for(Blockade blockade : blockades) {
                if(this.intersect(agentX, agentY, pX, pY, blockade)) {
                    if(this.getDistance(agentX, agentY, pX, pY) < this.clearDistance) {
                        Vector2D vector = this.scaleClear(this.getVector(agentX, agentY, pX, pY));
                        return new ActionClear((int)(agentX + vector.getX()), (int)(agentY + vector.getY()), blockade);
                    } else {
                        return new ActionMove(Lists.newArrayList(road.getID()), (int)pX, (int)pY);
                    }
                }
            }
        }
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean equalsPoint(double p1X, double p1Y, double p2X, double p2Y) {
        return this.equalsPoint(p1X, p1Y, p2X, p2Y, 1000.0D);
    }

    private boolean equalsPoint(double p1X, double p1Y, double p2X, double p2Y, double range) {
        return (p2X - range < p1X && p1X < p2X + range) && (p2Y - range < p1Y && p1Y < p2Y + range);
    }

    private boolean isInside(double pX, double pY, int[] apex) {
        Point2D p = new Point2D(pX, pY);
        Vector2D v1 = (new Point2D(apex[apex.length - 2], apex[apex.length - 1])).minus(p);
        Vector2D v2 = (new Point2D(apex[0], apex[1])).minus(p);
        double theta = this.getAngle(v1, v2);

        for(int i = 0; i < apex.length - 2; i += 2) {
            v1 = (new Point2D(apex[i], apex[i + 1])).minus(p);
            v2 = (new Point2D(apex[i + 2], apex[i + 3])).minus(p);
            theta += this.getAngle(v1, v2);
        }
        return Math.round(Math.abs((theta / 2) / Math.PI)) >= 1;
    }

    private boolean intersect(double agentX, double agentY, double pointX, double pointY, Area area) {
        for(Edge edge : area.getEdges()) {
            double startX = edge.getStartX();
            double startY = edge.getStartY();
            double endX = edge.getEndX();
            double endY = edge.getEndY();
            if(java.awt.geom.Line2D.linesIntersect(
                    agentX, agentY, pointX, pointY,
                    startX, startY, endX, endY
            )) {
                double midX = (edge.getStartX() + edge.getEndX()) / 2;
                double midY = (edge.getStartY() + edge.getEndY()) / 2;
                if(!equalsPoint(pointX, pointY, midX, midY) && !equalsPoint(agentX, agentY, midX, midY)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean intersect(Blockade blockade, Blockade another) {
        if(blockade.isApexesDefined() && another.isApexesDefined()) {
            int[] apexes0 = blockade.getApexes();
            int[] apexes1 = another.getApexes();
            for (int i = 0; i < (apexes0.length - 2); i += 2) {
                for (int j = 0; j < (apexes1.length - 2); j += 2) {
                    if (java.awt.geom.Line2D.linesIntersect(
                            apexes0[i], apexes0[i + 1], apexes0[i + 2], apexes0[i + 3],
                            apexes1[j], apexes1[j + 1], apexes1[j + 2], apexes1[j + 3]
                    )) {
                        return true;
                    }
                }
            }
            for (int i = 0; i < (apexes0.length - 2); i += 2) {
                if (java.awt.geom.Line2D.linesIntersect(
                        apexes0[i], apexes0[i + 1], apexes0[i + 2], apexes0[i + 3],
                        apexes1[apexes1.length - 2], apexes1[apexes1.length - 1], apexes1[0], apexes1[1]
                )) {
                    return true;
                }
            }
            for (int j = 0; j < (apexes1.length - 2); j += 2) {
                if (java.awt.geom.Line2D.linesIntersect(
                        apexes0[apexes0.length - 2], apexes0[apexes0.length - 1], apexes0[0], apexes0[1],
                        apexes1[j], apexes1[j + 1], apexes1[j + 2], apexes1[j + 3]
                )) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean intersect(double agentX, double agentY, double pointX, double pointY, Blockade blockade) {
        if(!blockade.isApexesDefined()) {
            return false;
        }
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

    private double getDistance(double fromX, double fromY, double toX, double toY) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        return Math.hypot(dx, dy);
    }

    private double getAngle(Vector2D v1, Vector2D v2) {
        double flag = (v1.getX() * v2.getY()) - (v1.getY() * v2.getX());
        double angle = Math.acos(((v1.getX() * v2.getX()) + (v1.getY() * v2.getY())) / (v1.getLength() * v2.getLength()));
        if(flag > 0) {
            return angle;
        }
        if(flag < 0) {
            return -1 * angle;
        }
        return 0.0D;
    }

    private Vector2D getVector(double fromX, double fromY, double toX, double toY) {
        return (new Point2D(toX, toY)).minus(new Point2D(fromX, fromY));
    }

    private Vector2D scaleClear(Vector2D vector) {
        return vector.normalised().scale(this.clearDistance);
    }

    private Set<Point2D> getMovePoints(Road road) {
        Set<Point2D> points = this.movePointCache.get(road.getID());
        if (points == null) {
            points = new HashSet<>();
            int[] apex = road.getApexList();
            for (int i = 0; i < apex.length; i += 2) {
                for (int j = i + 2; j < apex.length; j += 2) {
                    double midX = (apex[i] + apex[j]) / 2;
                    double midY = (apex[i + 1] + apex[j + 1]) / 2;
                    if (this.isInside(midX, midY, apex)) {
                        points.add(new Point2D(midX, midY));
                    }
                }
            }
            for (Edge edge : road.getEdges()) {
                double midX = (edge.getStartX() + edge.getEndX()) / 2;
                double midY = (edge.getStartY() + edge.getEndY()) / 2;
                points.remove(new Point2D(midX, midY));
            }
            this.movePointCache.put(road.getID(), points);
        }
        return points;
    }
}