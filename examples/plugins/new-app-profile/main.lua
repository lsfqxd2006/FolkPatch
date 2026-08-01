-- Optional policy plugin for newly installed user applications.
-- Without this plugin, FolkPatch does not automatically profile new apps.

local mode = get_config("mode")
if mode ~= "root" and mode ~= "exclude" then
    mode = "exclude"
end

return {
    post_fs_data = function()
        info("new-app-profile loaded; mode=" .. mode)
    end,

    service = function()
        info("new-app-profile service callback ready; mode=" .. mode)
    end,

    action = function()
        info("new-app-profile is working; mode=" .. mode)
    end,

    package_added = function(package_name, uid)
        info("Applying " .. mode .. " profile to " .. package_name .. " (" .. uid .. ")")
        return mode
    end,
}
