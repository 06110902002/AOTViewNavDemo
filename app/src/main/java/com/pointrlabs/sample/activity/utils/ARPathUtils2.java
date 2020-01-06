package com.pointrlabs.sample.activity.utils;

import android.content.Context;
import android.util.Log;

import com.pointrlabs.core.pathfinding.Path;
import com.pointrlabs.core.pathfinding.directions.PathDirection;
import com.sensetime.armap.constant.PointrConfig;
import com.sensetime.armap.entity.CADCoord;
import com.sensetime.armap.entity.PathModel;
import com.sensetime.armap.utils.ARUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Create By 刘铁柱
 * Create Date 2019-11-12
 * Sensetime@Copyright
 * Des:
 */
public class ARPathUtils2 {

    private static ARPathUtils2 arPathUtils;

    public static ARPathUtils2 getInstance(){
        if(arPathUtils == null){
            arPathUtils = new ARPathUtils2();
        }
        return arPathUtils;
    }

    /**
     * 需要先获得 计算出来的路径，然后进行插值，再转换为json串
     * @param context
     * @param calculatedPath
     * @return
     */
    public String getPathJSONString(Context context, Path calculatedPath){

        if(calculatedPath == null) return null;

        JSONObject returnObj = new JSONObject();
        JSONArray routeInfos = new JSONArray();
        String dirType = "";

        List<PathModel> pathModelList = new ArrayList<>();
        for(PathDirection des : calculatedPath.getDirections()){
            if(des.getMessage() == null || des.getStartNode() == null) continue;
            if(des.getStartNode().getLocation().getX()<= 0 || des.getStartNode().getLocation().getY() <= 0) continue;
            JSONObject routeObj = new JSONObject();
            if( des.getMessage().equals(context.getString(com.sensetime.armap.R.string.upper_floor_direction_message))){
                dirType = "up";
            }else if( des.getMessage().equals(context.getString(com.sensetime.armap.R.string.lower_floor_direction_message))){
                dirType = "down";
            }else{
                //dirType = des.getType() == null ? "":des.getType().name();
                dirType = des.getType() == null ? "": PointrConfig.getPathDirection(des.getType().name());

            }

            try {
                routeObj.put("_des",des.getMessage());
                routeObj.put("_directionString",dirType);

                String levelInfo = filterLevelInfo(des.getStartNode().getLocation().getLevel());

                CADCoord cadCoord = ARUtils.pointR2CAD(des.getStartNode().getLocation().getX(),
                        des.getStartNode().getLocation().getY(),levelInfo);
                System.out.println("69--------:"+levelInfo+" orix:"+des.getStartNode().getLocation().getX()+
                        "oriy:"+des.getStartNode().getLocation().getY()+
                        " cadx:"+cadCoord.getX() + " cady:"+cadCoord.getY());
                routeObj.put("_x",cadCoord.getX());
                routeObj.put("_z",cadCoord.getY());
                routeObj.put("_y",0.0f);
                routeInfos.put(routeObj);



                PathModel pathNodeEntity = new PathModel();
                pathNodeEntity.setX(cadCoord.getX());
                pathNodeEntity.setZ(cadCoord.getY());
                pathNodeEntity.setY(0.0);
                pathNodeEntity.setDes(des.getMessage());
                pathNodeEntity.setDirectionString(dirType);
                pathModelList.add(pathNodeEntity);



            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        try {
            returnObj.put("nodelist",routeInfos);
            String jsonStr = returnObj.toString();

            System.out.println("679----------插值之前:"+jsonStr);

            // return jsonStr;
//
            List<PathModel> interpolateList = interpolate2PathNode(pathModelList);
            return transformPathModel2JSONString(interpolateList);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;


        //处理终点
//        JSONObject routeObj = new JSONObject();
//
//        CADCoord endcadCoord = ARUtils.pointR2CAD(end.getX(),
//                end.getY());
//        try {
//            routeObj.put("_des", "Please follow the line");
//            routeObj.put("_directiontype", "抵达终点");
//            routeObj.put("_x", endcadCoord.getX());
//            routeObj.put("_z", endcadCoord.getY());
//            routeObj.put("_y", 0.0f);
//            routeInfos.put(routeObj);
//            returnObj.put("nodeList",routeInfos);
//
//
//            PathModel pathNodeEntity = new PathModel();
//            pathNodeEntity.setX(endcadCoord.getX());
//            pathNodeEntity.setZ(endcadCoord.getY());
//            pathNodeEntity.setY(0.0);
//            pathNodeEntity.setDes("Please follow the line");
//            pathNodeEntity.setDirectionString("抵达终点");
//            pathModelList.add(pathNodeEntity);
//
//        }catch (JSONException e) {
//            e.printStackTrace();
//        }

    }

    /**
     * 路径结点进行插值
     * @param pathModelList
     */
    private List<PathModel> interpolate2PathNode(List<PathModel> pathModelList){
        if(pathModelList == null || pathModelList.size() == 0) return null;

        List<PathModel> newNodeList = new ArrayList<>();
        for(int i = 0; i < pathModelList.size() - 1; i ++){
            PathModel node1 = pathModelList.get(i);
            PathModel node2 = pathModelList.get(i + 1);

            double distance = calDistance(node1.getX(),node1.getZ(),
                    node2.getX(),node2.getZ());

            if(distance > 5){
                int number = (int)distance / 2;     //每一段插值的个数
                System.out.println("123-------:number:"+number + " start index:"+ i);


                if(newNodeList.size() >= 1){

                    PathModel lastNode = newNodeList.get(newNodeList.size() - 1);
                    boolean hasAdd = (node1.getX() == lastNode.getX() && node1.getZ() == lastNode.getZ()) || newNodeList.contains(node1);
                    if(!hasAdd){
                        newNodeList.add(node1); //当不需要添加插值的时候，将原来的结点 移到新数组中
                    }

                }else{
                    newNodeList.add(node1); //每一段优先将第一个结点添加进去
                }

                double detlaX = (node2.getX() - node1.getX()) /number;
                double detlaZ = (node2.getZ() - node1.getZ()) /number;



                for(int j = 1; j < number; j ++){

                    //组装插值点
                    PathModel tmpNode = new PathModel();
                    tmpNode.setX(node1.getX() + j * detlaX);
                    tmpNode.setZ(node1.getZ() + j * detlaZ);
                    tmpNode.setY(0.0);
                    tmpNode.setDes("Please follow the line");
                    tmpNode.setDirectionString("forward");

                    newNodeList.add(tmpNode);   //将生成的插值结点添加到新的列表中

                }

                newNodeList.add(node2);

            }else{
                if(newNodeList.size() >= 1){

                    PathModel lastNode = newNodeList.get(newNodeList.size() - 1);
                    boolean hasAdd = (node1.getX() == lastNode.getX() && node1.getZ() == lastNode.getZ()) || newNodeList.contains(node1);
                    if(!hasAdd){
                        newNodeList.add(node1); //当不需要添加插值的时候，将原来的结点 移到新数组中
                    }

                }else{
                    newNodeList.add(node1); //每一段优先将第一个结点添加进去
                }



            }

        }

        //判断最后一个是否包含在内
        if(newNodeList.size() >= 1){
            PathModel lastNode = newNodeList.get(newNodeList.size() - 1);
            PathModel originLastNode = pathModelList.get(pathModelList.size() - 1);
            boolean hasAdd = (originLastNode.getX() == lastNode.getX() && originLastNode.getZ() == lastNode.getZ());
            if(!hasAdd){
                newNodeList.add(originLastNode); //当不需要添加插值的时候，将原来的结点 移到新数组中
            }
        }

        return newNodeList;
    }

    /**
     * 将路径导航的数据转换为json串
     * @param pathModelList
     */
    private String transformPathModel2JSONString(List<PathModel> pathModelList){
        if(pathModelList == null || pathModelList.size() == 0) return null;

        JSONObject returnObj = new JSONObject();
        JSONArray routeInfos = new JSONArray();
        String dirType = "";
        PathModel firstNode = pathModelList.get(0);
        for(int i = 0; i < pathModelList.size(); i ++){

            double tepDis = calDistance(firstNode.getX(),firstNode.getZ(),
                    pathModelList.get(i).getX(),pathModelList.get(i).getZ());
            if(tepDis < 3) {
                continue;
            }

            PathModel des = pathModelList.get(i);
            JSONObject routeObj = new JSONObject();

            try {
                if(i == pathModelList.size() - 1){
                    routeObj.put("_des","The End");
                }else{
                    routeObj.put("_des",des.getDes());
                }


                routeObj.put("_directionString",des.getDirectionString());

                routeObj.put("_x",des.getX());
                routeObj.put("_z",des.getZ());
                routeObj.put("_y",0.0);
                routeInfos.put(routeObj);


            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        try {
            returnObj.put("nodelist",routeInfos);
            String jsonStr = returnObj.toString();
            System.out.println("348----------插值之后:"+jsonStr);
            return jsonStr;
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }


    /**
     * 构建导航路径数据，逻辑如下：
     * 1.选出原路径的关键点数据（起始点，终点，拐点数据）
     * 2.对原路径的数据进行插值，其中高度保持不变即坐标中的z保持不变
     * 3.再插值之后的数据转换为json串返回
     * @param context
     * @param calculatedPath
     * @return
     */
    public String buildPathJSONString(Context context,Path calculatedPath){

        if(calculatedPath == null) return null;

        JSONObject returnObj = new JSONObject();
        JSONArray routeInfos = new JSONArray();
        String dirType = "";



        List<PathModel> pathModelList = new ArrayList<>();

        //找出第一个效点
        int firstNodeIndex = 0;
        for(int i = 0; i < calculatedPath.getDirections().size(); i ++){
            PathDirection des = calculatedPath.getDirections().get(i);
            if(des.getMessage() == null || des.getStartNode() == null) continue;
            if(des.getStartNode().getLocation().getX()<= 0 || des.getStartNode().getLocation().getY() <= 0) continue;

            String levelInfo = filterLevelInfo(des.getStartNode().getLocation().getLevel());
            CADCoord cadCoord = ARUtils.pointR2CAD(des.getStartNode().getLocation().getX(),
                    des.getStartNode().getLocation().getY(),levelInfo);
            PathModel firstNode = new PathModel();
            firstNode.setX(cadCoord.getX());
            firstNode.setZ(cadCoord.getY());
            firstNode.setY(0.0);
            firstNode.setDes(des.getMessage());
            firstNode.setDirectionString(dirType);
            pathModelList.add(firstNode);
            firstNodeIndex = i;

        }

        //过滤直线点，只保留关键点(起点，终点，拐点)
        for(int i = firstNodeIndex; i < calculatedPath.getDirections().size(); i ++){
            PathDirection des = calculatedPath.getDirections().get(i);
            if(des.getMessage() == null || des.getStartNode() == null) continue;
            if(des.getStartNode().getLocation().getX()<= 0 || des.getStartNode().getLocation().getY() <= 0) continue;
            if(des.getType().name().equals(PathDirection.PathDirectionType.GO_STRAIGHT)
                && i != calculatedPath.getDirections().size() - 1) continue;

            if( des.getMessage().equals(context.getString(com.sensetime.armap.R.string.upper_floor_direction_message))){
                dirType = "up";
            }else if( des.getMessage().equals(context.getString(com.sensetime.armap.R.string.lower_floor_direction_message))){
                dirType = "down";
            }else{
                dirType = des.getType() == null ? "": PointrConfig.getPathDirection(des.getType().name());

            }
            String levelInfo = filterLevelInfo(des.getStartNode().getLocation().getLevel());

            CADCoord cadCoord = ARUtils.pointR2CAD(des.getStartNode().getLocation().getX(),
                    des.getStartNode().getLocation().getY(),levelInfo);

            PathModel pathNodeEntity = new PathModel();
            pathNodeEntity.setX(cadCoord.getX());
            pathNodeEntity.setZ(cadCoord.getY());
            pathNodeEntity.setY(0.0);
            pathNodeEntity.setDes(des.getMessage());
            pathNodeEntity.setDirectionString(dirType);
            pathModelList.add(pathNodeEntity);
        }


        //测试代码  方便对比
        for(PathDirection des : calculatedPath.getDirections()){
            if(des.getMessage() == null || des.getStartNode() == null) continue;
            if(des.getStartNode().getLocation().getX()<= 0 || des.getStartNode().getLocation().getY() <= 0) continue;
            JSONObject routeObj = new JSONObject();
            if( des.getMessage().equals(context.getString(com.sensetime.armap.R.string.upper_floor_direction_message))){
                dirType = "up";
            }else if( des.getMessage().equals(context.getString(com.sensetime.armap.R.string.lower_floor_direction_message))){
                dirType = "down";
            }else{
                //dirType = des.getType() == null ? "":des.getType().name();
                dirType = des.getType() == null ? "": PointrConfig.getPathDirection(des.getType().name());

            }

            try {
                routeObj.put("_des",des.getMessage());
                routeObj.put("_directionString",dirType);

                String levelInfo = filterLevelInfo(des.getStartNode().getLocation().getLevel());

                CADCoord cadCoord = ARUtils.pointR2CAD(des.getStartNode().getLocation().getX(),
                        des.getStartNode().getLocation().getY(),levelInfo);
                System.out.println("69--------:"+levelInfo+" orix:"+des.getStartNode().getLocation().getX()+
                        "oriy:"+des.getStartNode().getLocation().getY()+
                        " cadx:"+cadCoord.getX() + " cady:"+cadCoord.getY());
                routeObj.put("_x",cadCoord.getX());
                routeObj.put("_z",cadCoord.getY());
                routeObj.put("_y",0.0f);
                routeInfos.put(routeObj);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        try {
            returnObj.put("nodelist",routeInfos);
            String jsonStr = returnObj.toString();
            System.out.println("349----------插值之前:"+jsonStr);
            List<PathModel> interpolateList = interpolate2PathNode(pathModelList,1.5f);
            return transformPathModel2JSONString(interpolateList);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;

    }

    /**
     * 修正路径，每隔threshold米进行 一个差值
     *
     * @param pathModelList 只保留了原路径的关键点数据，起始点，终点，拐点数据
     * @return
     */
    private List<PathModel> interpolate2PathNode(List<PathModel> pathModelList,float threshold){

        if(pathModelList == null || pathModelList.size() == 0) return null;

        List<PathModel> newNodeList = new ArrayList<>();
        for(int i = 0; i < pathModelList.size() - 1; i ++){

            PathModel node1 = pathModelList.get(i);
            PathModel node2 = pathModelList.get(i + 1);

            double distance = calDistance(node1.getX(),node1.getZ(),
                    node2.getX(),node2.getZ());
            if(distance > threshold){

                int number = (int)(distance / threshold);     //每一段插值的个数
                for(int j = 1; j < number; j ++){

                    //组装插值点
                    PathModel tmpNode = new PathModel();
                    tmpNode.setX(node1.getX() + j * threshold);
                    tmpNode.setZ(node1.getZ() );
                    tmpNode.setY(0.0);
                    tmpNode.setDes("Please follow the line");
                    tmpNode.setDirectionString("forward");

                    newNodeList.add(tmpNode);   //将生成的插值结点添加到新的列表中
                }
                newNodeList.add(node2);

            }else{
                if(newNodeList.size() >= 1){
                    PathModel lastNode = newNodeList.get(newNodeList.size() - 1);
                    boolean hasAdd = (node1.getX() == lastNode.getX() && node1.getZ() == lastNode.getZ()) || newNodeList.contains(node1);
                    if(!hasAdd){
                        newNodeList.add(node1); //当不需要添加插值的时候，将原来的结点 移到新数组中
                    }

                }else{
                    newNodeList.add(node1); //每一段优先将第一个结点添加进去
                }
            }

        }
        return newNodeList;
    }



    /**
     * 计算2点之间的距离
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     * @return
     */
    private double calDistance(double x1,double y1,double x2,double y2){

        double c = 0;
        double i = Math.pow((x1 - x2), 2);
        double j = Math.pow((y1 - y2), 2);
        c = Math.sqrt(i + j);
        return c;

    }

    private String filterLevelInfo(int pointrLevel){
        String levelInfo = "F4";
        if(pointrLevel > 1){
            levelInfo = "F"+(pointrLevel - 1);
        }else if(pointrLevel == 0 || pointrLevel == 1){
            levelInfo = "F1";
        }else{
            levelInfo = "B1";
        }
        return levelInfo;
    }


}
