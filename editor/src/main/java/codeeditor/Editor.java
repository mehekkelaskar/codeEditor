package codeeditor;


import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
@ServerEndpoint("/codeeditor")
public class Editor {

	 private static Map<String, Set<Session>> rooms = new ConcurrentHashMap<>();
	    private static Map<Session, String> sessionToRoom = new ConcurrentHashMap<>();
	    private static Map<Session, String> usernames = new ConcurrentHashMap<>();
	    private static Map<String, String> roomCodes = new ConcurrentHashMap<>();

	    @OnOpen
	    public void onOpen(Session session) {
	    	System.out.println("New connection"+session.getId());
	    }


	    private static final Gson gson = new Gson();

	    @OnMessage
	    public void onMessage(String message, Session session) throws IOException {
	        JsonObject data = gson.fromJson(message, JsonObject.class);
	        String type = data.get("type").getAsString();

	        if ("join".equals(type)) {
	            String roomId = data.get("roomId").getAsString();
	            String username = data.get("username").getAsString();

	            rooms.putIfAbsent(roomId, ConcurrentHashMap.newKeySet());
	            rooms.get(roomId).add(session);
	            sessionToRoom.put(session, roomId);
	            usernames.put(session, username);

	            String existingCode = roomCodes.getOrDefault(roomId, "");
	            JsonObject codeMsg = new JsonObject();
	            codeMsg.addProperty("type", "codeUpdate");
	            codeMsg.addProperty("code", existingCode);

	            session.getBasicRemote().sendText(gson.toJson(codeMsg));
	            updateMembersList(roomId);
	        } else if ("codeUpdate".equals(type)) {
	            String roomId = data.get("roomId").getAsString();
	            String code = data.get("code").getAsString();

	            roomCodes.put(roomId, code);

	            JsonObject codeMsg = new JsonObject();
	            codeMsg.addProperty("type", "codeUpdate");
	            codeMsg.addProperty("code", code);

	            broadcastToRoom(roomId, gson.toJson(codeMsg), session);
	        }
	    }


	    @OnClose
	    public void onClose(Session session) throws IOException {
	        String roomId = sessionToRoom.get(session);
	        if (roomId != null && rooms.containsKey(roomId)) {
	            rooms.get(roomId).remove(session);
	            updateMembersList(roomId);
	        }
	        usernames.remove(session);
	        sessionToRoom.remove(session);
	        System.out.println("User with id "+session.getId()+"is disconnected");
	    }

	    private void updateMembersList(String roomId) throws IOException {
	        Set<Session> members = rooms.get(roomId);
	        if (members == null) return;

	        List<String> memberNames = new ArrayList<>();
	        for (Session s : members) {
	            memberNames.add(usernames.get(s));
	        }

	        String message = "{\"type\":\"updateMembers\",\"members\":" + toJsonArray(memberNames) + "}";
	        for (Session s : members) {
	            if (s.isOpen()) {
	                s.getBasicRemote().sendText(message);
	            }
	        }
	    }

	    private void broadcastToRoom(String roomId, String message, Session sender) throws IOException {
	        for (Session s : rooms.get(roomId)) {
	            if (s.isOpen() && !s.equals(sender)) {
	                s.getBasicRemote().sendText(message);
	            }
	        }
	    }

	    private Map<String, String> parseJson(String json) {
	        Map<String, String> map = new HashMap<>();
	        json = json.replaceAll("[{}\"]", "");
	        String[] parts = json.split(",");
	        for (String part : parts) {
	            String[] kv = part.split(":");
	            if (kv.length == 2) {
	                map.put(kv[0].trim(), kv[1].trim());
	            }
	        }
	        return map;
	    }

	    private String toJsonArray(List<String> list) {
	        StringBuilder sb = new StringBuilder("[");
	        for (int i = 0; i < list.size(); i++) {
	            sb.append("\"").append(list.get(i)).append("\"");
	            if (i != list.size() - 1) sb.append(",");
	        }
	        sb.append("]");
	        return sb.toString();
	    }

	    private String escapeJson(String s) {
	        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	    }
	}