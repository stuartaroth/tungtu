package org.tungtu.identity.data;

import org.json.JSONObject;

public interface DataService {
    JSONObject login(String username, String password);
}
