(ns build
  (:refer-clojure :exclude [test])
  (:require
   [clojure.string :as string]
   [clojure.tools.build.api :as b]
   [org.corfield.build :as bb]))

(def ^:private lib 'com.github.mainej/re-stated)
(def ^:private rev-count (Integer/parseInt (b/git-count-revs nil)))
(def ^:private semantic-version "0.2")
(defn- format-version [revision] (format "%s.%s" semantic-version revision))
(def ^:private version (format-version rev-count))
(def ^:private next-version (format-version (inc rev-count)))
(def ^:private tag (str "v" version))
(def ^:private basis (b/create-basis {:root    nil
                                      :user    nil
                                      :project "deps.edn"}))

(defn- die
  ([message & args]
   (die (apply format message args)))
  ([message]
   (binding [*out* *err*]
     (println message))
   (System/exit 1)))

(defn- git [command args]
  (b/process (assoc args :command-args (into ["git"] command))))

(defn- git-rev []
  (let [{:keys [exit out]} (git ["rev-parse" "HEAD"] {:out :capture})]
    (when (zero? exit)
      (string/trim out))))

(defn- git-push [params]
  (when-not (and (zero? (:exit (git ["push" "origin" tag] {})))
                 (zero? (:exit (git ["push" "origin"] {}))))
    (die "\nCouldn't sync with github."))
  params)


(defn- assert-changelog-updated [params]
  (when-not (string/includes? (slurp "CHANGELOG.md") tag)
    (die (string/join "\n"
                      ["CHANGELOG.md must include tag."
                       "  * If you will amend the current commit, use %s"
                       "  * If you intend to create a new commit, use %s"]) version next-version))
  params)

(defn- assert-scm-clean [params]
  (let [git-changes (:out (git ["status" "--porcelain"] {:out :capture}))]
    (when-not (string/blank? git-changes)
      (println git-changes)
      (die "Git working directory must be clean. Run `git commit`")))
  params)

(defn- assert-scm-tagged [params]
  (when-not (zero? (:exit (git ["rev-list" tag] {:out :ignore})))
    (die "\nGit tag %s must exist. Run `bin/tag-release`" tag))
  (let [{:keys [exit out]} (git ["describe" "--tags" "--abbrev=0" "--exact-match"] {:out :capture})]
    (when-not (and (zero? exit)
                   (= (string/trim out) tag))
      (die (string/join "\n"
                        [""
                         "Git tag %s must be on HEAD."
                         ""
                         "Proceed with caution, because this tag may have already been released. If you've determined it's safe, run `git tag -d %s` before re-running `bin/tag-release`."]) tag tag)))
  params)

#_{:clj-kondo/ignore #{:clojure-lsp/unused-public-var}}
(defn tag-release "Tag the HEAD commit for the current release." [params]
  (when-not (zero? (:exit (git ["tag" tag] {})))
    (die "\nCouldn't create tag %s." tag))
  params)

(defn clean "Remove the target folder." [params]
  (-> params bb/clean))

(defn jar "Build the library JAR file." [params]
  (-> params
      (assoc :lib     lib
             :version version
             :basis   basis
             :tag     (git-rev))
      bb/jar))

(defn test
  "Run the tests."
  [params]
  (-> params bb/run-tests))

(defn check-release
  "Check that the library is ready to be released.

  * Tests pass
  * No outstanding commits
  * Git tag for current release exists in local repo
  * CHANGELOG.md references new tag"
  [params]
  (-> params
      test
      assert-changelog-updated
      ;; after assertions about content, so any change can be committed/amended
      assert-scm-clean
      ;; last, so that correct commit is tagged
      assert-scm-tagged))

#_{:clj-kondo/ignore #{:clojure-lsp/unused-public-var}}
(defn release
  "Release the library.

  * Confirm that we are ready to release
  * Build the JAR
  * Deploy to Clojars
  * Ensure the tag is available on Github"
  [params]
  (-> params
      (assoc :lib     lib
             :version version)
      check-release
      clean
      jar
      bb/deploy
      git-push))
