package adf.sample.extaction;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.police.ActionClear;
import adf.agent.debug.DebugData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import adf.sample.SampleModuleKey;
import com.google.common.collect.Lists;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

public class ActionExtClear extends ExtAction {
    private int clearDistance;

    private EntityID target;
    private Map<EntityID, Set<Edge>> edgeCache;
    private Map<EntityID, Set<Point2D>> movePointCache;

    public ActionExtClear(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DebugData debugData) {
        super(ai, wi, si, moduleManager, debugData);
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
        Road targetRoad = (Road)this.worldInfo.getEntity(this.target);
        if(this.target.getValue() == agentPosition.getValue()) {
            this.result = this.calcTargetPosition(policeForce, targetRoad);
            if(this.result != null) return this;
        } else if(targetRoad.getEdgeTo(agentPosition) != null) {
            this.result = this.calcNeighbourPosition(policeForce, targetRoad);
            if(this.result != null) return this;
        } else {
            this.result = this.calcOtherPosition(policeForce, targetRoad);
            if(this.result != null) return this;
        }
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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
                double clearX = agentX + vector.getY();
                double clearY = agentY + vector.getY();
                for(Blockade blockade : blockades) {
                    if(this.intersect(agentX, agentY, clearX, clearY, blockade)) {
                        checkEdges.removeAll(removeEdges);
                        this.edgeCache.put(targetRoad.getID(), checkEdges);
                        return new ActionClear((int)clearX, (int)clearY, blockade);
                    }
                    if(this.intersect(agentX, agentY, midX, midY, blockade)) {
                        checkEdges.removeAll(removeEdges);
                        this.edgeCache.put(targetRoad.getID(), checkEdges);
                        return new ActionMove(Lists.newArrayList(targetRoad.getID()), (int)midX, (int)midY);
                    }
                }
                removeEdges.add(edge);
            }
        }
        checkEdges.removeAll(removeEdges);
        this.edgeCache.put(targetRoad.getID(), checkEdges);

        Set<Point2D> points = this.movePointCache.get(targetRoad.getID());
        if(points == null) {
            points = this.createMovePoints(targetRoad);
            this.movePointCache.put(targetRoad.getID(), points);
        }
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
                clearX = agentX + vector.getY();
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
                    points = this.createMovePoints(targetRoad);
                    this.movePointCache.put(targetRoad.getID(), points);
                }
                Action action = this.calcIntersectEdgeAction(agentX, agentY, edge, positionRoad, points, blockades);
                if (action != null) return action;
            } else {
                double distance = this.getDistance(agentX, agentY, midX, midY);
                if (distance < this.clearDistance) {
                    Vector2D vector = this.scaleClear(this.getVector(agentX, agentY, midX, midY));
                    double clearX = agentX + vector.getY();
                    double clearY = agentY + vector.getY();
                    for (Blockade blockade : this.worldInfo.getBlockades(positionRoad)) {
                        if (this.intersect(agentX, agentY, clearX, clearY, blockade)) {
                            return new ActionClear((int) clearX, (int) clearY, blockade);
                        }
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
            PathPlanning pathPlanning = this.moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING);
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
        Point2D bestPoint = null;
        Point2D subPoint = null;
        double bastDistance  = Double.MAX_VALUE;
        double edgeMinDistance  = Double.MAX_VALUE;
        double agentMaxDistance = Double.MIN_VALUE;
        for(Point2D p : movePoints) {
            if(!this.intersect(agentX, agentY, p.getX(), p.getY(), road)) {
                double distance = this.getDistance(midX, midY, p.getX(), p.getY());
                if(distance < bastDistance) {
                    if(!this.intersect(midX, midY, p.getX(), p.getY(), road)) {
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
        return this.equalsPoint(p1X, p1Y, p2X, p2Y, 1.0D);
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

    private Set<Point2D> createMovePoints(Road road) {
        Set<Point2D> points = new HashSet<>();
        int[] apex = road.getApexList();
        for(int i = 0; i < apex.length; i += 2) {
            for(int j = i + 2; i < apex.length; j += 2) {
                double midX = (apex[i] + apex[j]) / 2;
                double midY = (apex[i + 1] + apex[j + 1]) / 2;
                if(this.isInside(midX, midY, apex)) {
                    points.add(new Point2D(midX, midY));
                }
            }
        }
        for(Edge edge : road.getEdges()) {
            double midX = (edge.getStartX() + edge.getEndX()) / 2;
            double midY = (edge.getStartY() + edge.getEndY()) / 2;
            points.remove(new Point2D(midX, midY));
        }
        return points;
    }
}